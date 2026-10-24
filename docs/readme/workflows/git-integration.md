# Git integration

OpenJCockpit delivers agent work as ordinary Git history: a branch per run in your project's
repository, with traceable commits that your team reviews and merges through its normal process.
Git operations go through the policy-gated [Git MCP server](../integrations/mcp-tools.md), reached
via the LiteLLM gateway. Credentials are resolved server-side and never pass through an LLM.

The authoritative detail for this page is §9b of the agent-integration operations guide
([`OPERATIONS.md`](../../../services/embabel-agent-service/docs/agent-integration/OPERATIONS.md), Dutch).

## Project setup

The repository URL and Git credentials come from the selected **project**. Add the project and its
repository URL in the dashboard, then add a credential under **Settings → Git credentials**.
Credentials are stored encrypted by `ai-control-service` (`ENCRYPTION_SECRET` / `ENCRYPTION_SALT`; the
Compose defaults are local-only placeholders). For GitHub token scopes needed by the spec queue, see
[Spec queue](spec-queue.md#github-token-scopes).

## Dashboard starts on a project run in a Git workspace

When a dashboard start carries a `projectId` whose project has a repository:

1. `ai-control-service` creates a job, synchronously clones the repository (before the first agent)
   into the workspace `job-<job-id>` and creates the branch
   `<branch-prefix>/<workflow>/<yyyyMMdd-HHmmss>-<job>`: one branch per run. The prefix is
   `openjcockpit.agentic-workflow.branch-prefix`, default `agentic`.
2. `ai-control-service` (using its service identity) has `embabel-agent-service` issue a
   **git-workspace ticket** for that workspace, that workflow and the user's `sub`.
3. The run starts with the **user's own token** plus the ticket: OPA (`workflow.start`, agents)
   evaluates the user, and the run is recorded in their name.
4. Checkpoints after every agent and a final push by `ai-control-service` follow (see below). All agents
   of the run share the same workspace: the developer reads what the analyst and architect wrote.

The start response reports the branch. If the clone fails, the result is `502` and no run; a blocked
start marks the job `FAILED`. Starts without a project (or with a project without a repository) run
without a workspace.

## Git-workspace jobs

`ai-control-service` can also start jobs directly (`POST /api/agentic-workflows/start`, requires a
`workflowId`). Preflight refuses (`409`) an unknown workflow (`WORKFLOW_NOT_FOUND`) or a workflow without
linked agents (`WORKFLOW_HAS_NO_AGENTS`). The flow:

1. **Clone + branch** in the job workspace `job-<job-id>` of `git-mcp-server`, via LiteLLM
   (`git_create_branch`, key `ai-control-service`).
2. **Workflow start** in `embabel-agent-service` with `ai-control-service`'s own service identity
   (Keycloak client `openjcockpit-ai-control`) and a git-workspace ticket; the run is bound to the
   workspace.
3. **Agents** get the Git tools `git.list_files`, `git.read_file` (all four seeded agents) and
   `git.write_file` (Software Developer, QA Engineer), granted by definition (`tools:`) **and** tenant
   binding. The tools use the run's repository and workspace, never LLM input, and go through LiteLLM.
4. **Checkpoint after every agent** (`GitWorkspaceCheckpoints`): the full output is written to the
   workspace as `agent-output/<run-id>/<NN>-<agent>.md`, followed by `git_commit`
   (`checkpoint n/N: <agent>`) and `git_push` of the run branch, before the next agent starts. A failed
   checkpoint is a run event, not an error. Disable with `GIT_WORKSPACE_CHECKPOINTS=false`.
5. **Final commit + push** by `ai-control-service` when the run finishes. "Nothing to commit" is normal
   (everything was already in the checkpoints); the branch is pushed anyway, so a failed checkpoint push
   is retried here. A run that does not end `COMPLETED` marks the job `FAILED`, without a final push; the
   pushed checkpoints remain.

The final status (`COMPLETED`/`FAILED`) remains after cleanup (cleanup is an event, not a status).

## Git-workspace tickets

`embabel-agent-service` issues tickets via `POST /internal/git-workspace-tickets`. Only
`azp=openjcockpit-ai-control` may issue them (otherwise `403`). A ticket belongs to one workspace and
branch, one workflow and one `sub` (omitted means `ai-control-service` itself, for jobs). It is valid
for 2 minutes (`GIT_WORKSPACE_TICKET_TTL`) and consumed on the first redemption attempt, even an
invalid one. Starting with a ticket that does not match is `BLOCKED`. At most 1 000 outstanding tickets
exist, in memory only, never logged. A `gitWorkspaceTicket` sent by a caller is stripped by
`ai-control-service`.

## Git credentials never reach LiteLLM or an LLM

No Git tool has a `username`/`token` argument. `git-mcp-server` fetches the credentials of a workspace
itself from `ai-control-service`
(`GET /internal/git-workspaces/{key}/credentials?repositoryUrl=…`, Keycloak client
`openjcockpit-git-mcp`): only for that client (`azp`, otherwise `403`), only while the job runs, and only
for the job's project repository (otherwise `404`); `Cache-Control: no-store`, never logged. This endpoint
is not in the public contract and not reachable through the dashboard proxy (`/api/` only). A workspace
without a key is anonymous; a key without credentials fails closed.

Git credentials in `WorkflowStartInput`/`AgentRunRequest` (`gitUsername`/`gitToken`) still exist for
dashboard starts; jobs do not use them.

## Pull requests and merging

Workflow runs deliver a **pushed branch**. Opening the pull request is part of your review process:

- `git-mcp-server` offers a `git_create_pull_request` tool (GitHub only), but the seeded agents are not
  granted it, and the default workflows do not call it.
- The [spec queue](spec-queue.md) can auto-merge an item's pull request when auto-merge is enabled and
  the run reports exactly one `pull-request:` artifact. Merging is GitHub-only.

> **Verify before relying on auto-merge.** In the current code, no default workflow or job step emits a
> `pull-request:` run artifact, so spec-queue items wait for a human merge unless a custom
> workflow produces one. Earlier versions opened pull requests automatically; see
> [Historical notes](../reference/historical-notes.md#spec-and-implementation-publishing-to-git-deterministic).

## Spec initialization branch

If a project repository has no `.specify` folder on its base branch, the dashboard offers the spec-init
flow: `ai-control-service` pushes a starter template on a `spec-init/<project-slug>/…` branch to be
reviewed as a pull request (or links to an already open spec-init branch). See
[Spec-driven development](spec-driven-development.md#spec-init-and-create-flows-in-the-dashboard).

## Known limitations of workspace runs

- A job follows the run for at most `WORKFLOW_AGENT_TIMEOUT` (locally `3600` s), **also while an approval
  gate is waiting**, and then stops the run.
- Tickets and run scopes are in memory: restarting `embabel-agent-service` drops running bindings.
- Orb-child workflows do not inherit the workspace.
- The job container image (`eclipse-temurin:25-jre`) is not pulled automatically.
- A job that is running while `ai-control-service` restarts stays at `AGENTS_RUNNING`.

## Related

- [MCP tools](../integrations/mcp-tools.md)
- [Agents and workflows](agents-and-workflows.md)
- [Spec queue](spec-queue.md)
- [Security considerations](../governance/security-considerations.md)
