# MCP: central tools for agents (`git-mcp-server`)

The platform tools for agents run through the [Model Context Protocol](https://modelcontextprotocol.io/)
(MCP), so the implementation lives in one place and every agent (or external MCP client) uses the
same tools. Every tool call is policy-checked before it executes. See
[Policy enforcement](../governance/policy-enforcement.md).

## Server: `services/git-mcp-server`

`git-mcp-server` is a Spring AI MCP server (port `8093`, SSE transport) that exposes the Git
integration as tools. Each tool works in a managed workspace clone of the project repository:

| Tool                      | Purpose                                                                                |
| ------------------------- | -------------------------------------------------------------------------------------- |
| `git_checkout_branch`     | Check out an existing branch (clones the repository if needed)                         |
| `git_create_branch`       | Create a new branch from a base branch (with a prior pull)                             |
| `git_pull`                | Fetch the latest changes for the active branch                                         |
| `git_list_files`          | List files in the workspace clone                                                      |
| `git_read_file`           | Read a text file from the workspace clone                                              |
| `git_write_file`          | Write a text file to a relative path in the workspace clone (e.g. `specs/spec-001.md`) |
| `git_commit`              | Stage and commit all changes (returns the commit hash)                                 |
| `git_push`                | Push the branch to origin so a pull request can be opened                              |
| `git_create_pull_request` | Open a pull request on GitHub for a pushed branch (returns the PR URL)                 |

The JGit implementation sits behind a `GitWorkspaceOperations` port (the same pattern as in
`ai-control-service`), and GitHub calls sit behind a `GitHubOperations` port: if the Git
implementation changes, that happens in one place.

### How it is reached

`git-mcp-server` is **not published on the host**. It has no authentication of its own, so the only
way in is through the LiteLLM gateway:

- `infrastructure/litellm/config.yaml` registers it under `mcp_servers.openjcockpit_git`
  (SSE, `http://git-mcp-server:8093/sse`, pinned `server_id`, `allow_all_keys: false`, private
  network only). Through LiteLLM, tools appear as `openjcockpit_git-<tool>`.
- `local-seed` grants it to three LiteLLM keys only: the `embabel-agent-runtime` model key (agents' Git tools),
  the MCP-only `embabel-agent-service-mcp` key (checkpoint commits)
  and the `ai-control-service` key (git-workspace jobs; MCP REST bridge routes only).

Quick check against a running stack:

```bash
curl -s -H "Authorization: Bearer $LITELLM_VIRTUAL_KEY" http://localhost:4100/mcp-rest/tools/list
```

See [`infrastructure/litellm/README.md`](../../../infrastructure/litellm/README.md#git-mcp-server).

### Git credentials never pass through an LLM

No Git tool takes a username or token argument. For job workspaces (`workspaceKey=job-<uuid>`),
`git-mcp-server` pulls the credentials itself from `ai-control-service`, using its own Keycloak
service client `openjcockpit-git-mcp`: only while the job runs, only for the job's project
repository, never logged. A workspace without a key is anonymous; a key without credentials fails
closed. Details: [Git integration](../workflows/git-integration.md#git-credentials-never-reach-litellm-or-an-llm).

## Clients

### Agent tools in `embabel-agent-runtime`

Catalogue agents receive Git tools by **definition** (`tools:` in the agent's Markdown front matter)
**and** by **tenant binding** (`agent_tenant_binding_tools`). The seeded agents use
`git.list_files`, `git.read_file` and (for the software developer and QA engineer) `git.write_file`.
These tools always use the run's own repository and workspace, never values chosen by the LLM, and
reach `git-mcp-server` through LiteLLM. Each call is authorized at call time against the agent's
snapshot and OPA (`SnapshotBoundTool` → `AgentServiceToolCallAuthorizer`, which asks
`embabel-agent-service` for the OPA decision; no decision means deny). The checkpoint commits after each
agent are made by `embabel-agent-service` itself, with its own MCP-only LiteLLM key.

### Central MCP client (`RemoteMcpToolExecutor`)

`embabel-agent-service` also contains a central MCP client: `RemoteMcpToolExecutor` connects to one or
more MCP servers (configuration `openjcockpit.mcp.servers`, MCP Java SDK over SSE) and routes every
tool call to the first server that offers the tool. Tool calling stays centralized: agents only reach
tools through `PolicyGuardedMcpToolGateway`, so every call first passes the OPA policy check. It is
enabled with `MCP_CLIENT_ENABLED=true` (`openjcockpit.mcp.enabled`; defaults to `false` in
`application.yml`); when disabled, behaviour falls back to the no-op executor.

> **Documentation discrepancy to be aware of.** `docker-compose.yml` still sets
> `MCP_CLIENT_ENABLED=true` and `GIT_MCP_SERVER_URL=http://git-mcp-server:8093` for
> `embabel-agent-service`, while the agent-integration operations guide
> ([`OPERATIONS.md`](../../../services/embabel-agent-service/docs/agent-integration/OPERATIONS.md), §9b)
> describes this direct client as switched off in favour of the LiteLLM route. Check the effective
> configuration of your deployment.

## Per-run workspace isolation (`workspaceKey`)

All Git tools accept an optional `workspaceKey` parameter. When supplied,
it is validated against `^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$` and rejected before
any filesystem access if it does not match. When accepted, it isolates that
call's working directory from other concurrent runs against the same
repository. When omitted or blank, behaviour is unchanged from before this
parameter existed (the legacy shared-workspace path).

Two environment-overridable tunables govern cleanup of these per-run
workspace directories, following the project's `${VAR:-default}` convention:

| Variable                              | Default | What it bounds                                                                       |
| -------------------------------------- | ------- | ---------------------------------------------------------------------------------------- |
| `GIT_MCP_RUN_WORKSPACE_RETENTION`      | `PT24H` | How long a per-run workspace directory is retained before it is eligible for reaping.    |
| `GIT_MCP_RUN_WORKSPACE_REAP_INTERVAL`  | `PT1H`  | How often the reaper scans for expired per-run workspace directories.                    |

**Rollback remedy for leftover `runs/` directories.** If `git-mcp-server` is
rolled back to a version that predates per-run workspace isolation, any
`runs/` subdirectories left behind under the workspace base path do not harm
the older version (it never looks there) but do consume disk space until the
retention window would otherwise have reaped them. The targeted remedy is a
`rm -rf` of `/git-mcp-workspaces/runs` **inside the running container** (not
the whole volume). Do **not** run `docker compose down -v` as a "quick fix"
for this: that command destroys the *entire* `git-mcp-workspaces` volume,
including the shared legacy workspace clones every run still depends on, and
this repository's operating rules require explicit user permission before any
such destructive volume action.

### Resolved: per-run workspace isolation replaces the one shared Git working copy per repository

`git-mcp-server` used to keep a single, shared working copy per repository
URL, so two runs operating on the same repository at the same time could
interfere with each other's checked-out branch. This was flagged as a
pre-existing limitation, made more noticeable by the approval-gate feature's
"accept with comments" retry loop, which kept a run active against a
repository for longer and widened the collision window.

This limitation is now resolved for callers that opt in: the optional
`workspaceKey` parameter isolates each run's working directory from every other concurrent run
against the same repository. The platform's own runs use a workspace key per job
(`job-<id>`). A caller that talks to `git-mcp-server` directly and omits `workspaceKey` still gets
the legacy shared-workspace behaviour unchanged.

## Transport and security limitations

- The MCP transport is pinned to SSE (`spring.ai.mcp.server.protocol: sse`).
- `git-mcp-server` has no authentication of its own; it relies on not being published and on
  LiteLLM key permissions.
- MCP tool-call fidelity through the LiteLLM gateway is not covered end to end by automated tests.

Details for all three: [Known limitations](../operations/known-limitations.md).

## Related

- [Policy enforcement](../governance/policy-enforcement.md)
- [Git integration](../workflows/git-integration.md)
- [LLM providers](llm-providers.md)
