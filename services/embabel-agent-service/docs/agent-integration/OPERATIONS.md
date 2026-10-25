# Configuration-driven agents + A2A — operations guide (SPEC-002)

This guide is self-contained: a developer can reproduce the local demo with it without any prior
context. Progress and evidence per step: `IMPLEMENTATION_PROGRESS.md`.

> **Since the management/execution split** the configuration-driven agents, the inbound A2A endpoints,
> the `openjcockpit.agent-definitions.*` settings, the LLM routes and `agent-definitions/` run in
> **`embabel-agent-runtime`** (`services/embabel-agent-runtime`, port 8092 locally), no longer in
> `embabel-agent-service`. Wherever `embabel-agent-service` appears below as the A2A server or agent runtime,
> read `embabel-agent-runtime`. Tenant bindings (`agent_tenant_bindings`), workflow runs and
> OPA decisions remain owned by `embabel-agent-service`; the runtime reads them via
> `/internal/agent-runtime/v1/**`. See `docs/readme/concepts/agent-runtime.md`.

## 1. What it does

```
A2A client ──► LiteLLM A2A Gateway (/a2a/customer-support)
                 │  x-litellm-api-key: Bearer <virtual key>
                 │  x-a2a-customer-support-authorization: Bearer <Keycloak JWT>  → forwarded as `authorization`
                 ▼
embabel-agent-service  POST /a2a/{agentId}  (A2A 0.3, JSON-RPC, message/send only)
  ├─ JWT validation (existing resource server) → tenant from claim `tenant-claim`
  ├─ AgentDefinitionResolver (1× per run)
  │    PostgreSQL agent_tenant_bindings (tenant ↔ agent, tool allowlist, mandatory guardrails, pins, fallback permission)
  │    source: REMOTE (LiteLLM, UNSUPPORTED in v1.101.2) → LAST_KNOWN_GOOD → LOCAL agents/<id>.md
  │    skills (skills/<id>/SKILL.md), tools (server registry ∩ allowlist), guardrails (executor required), model alias
  │    ⇒ immutable ResolvedAgentDefinition + PreparedAgent
  ├─ ConfiguredDefinitionAgent (Embabel @Agent/@Action)
  │    ai.withLlmByRole(<model alias>) + AgentPromptContributor + native Embabel Skills (materialised per run)
  │    + SnapshotBoundTool (call-time snapshot and OPA check) + Embabel GuardRails + strict ToolNotFoundPolicy
  └─ LLM call via LiteLLM model gateway (model = alias, e.g. customer-chat; policy openjcockpit-agent-pii)
```

Important: for its model calls the Embabel service calls the LiteLLM **model** route
(`/v1/chat/completions`), never its own `a2a/<agent>` route — there is no loop.

## 2. Configuration (`openjcockpit.agent-definitions.*`)

| Property | Env var | Default | Meaning |
|---|---|---|---|
| `enabled` | `AGENT_DEFINITIONS_ENABLED` | `false` | Whole feature on/off. Off = no bean active at all. |
| `local-root` | `AGENT_DEFINITIONS_LOCAL_ROOT` | – | Directory containing `agents/` and `skills/`. Required when `enabled`. |
| `tenant-claim` | `AGENT_DEFINITIONS_TENANT_CLAIM` | `tenant_id` | JWT claim holding the tenant id. Missing → refused. |
| `source-priority` | – | `REMOTE,LAST_KNOWN_GOOD,LOCAL` | Order of sources. |
| `mandatory-guardrails` | – | `[]` | Guardrails every agent must have, regardless of its definition. |
| `fallback.on-unavailable` | `AGENT_DEFINITIONS_FALLBACK_ON_UNAVAILABLE` | `true` | Fallback on `UNAVAILABLE` (plus per-agent permission). |
| `fallback.on-not-found` | `AGENT_DEFINITIONS_FALLBACK_ON_NOT_FOUND` | `false` | Fallback on `NOT_FOUND` (plus per-agent `fallback_on_not_found`). |
| `cache.ttl` | `AGENT_DEFINITIONS_CACHE_TTL` | `1h` | Validity of a last-known-good snapshot. |
| `cache.directory` | `AGENT_DEFINITIONS_CACHE_DIR` | – | Directory for snapshots; empty = no last-known-good source. |
| `cache.max-entries` | – | `256` | Maximum number of snapshots. |
| `litellm.enabled` | `AGENT_DEFINITIONS_LITELLM_ENABLED` | `false` | LiteLLM as metadata source + gateway guardrail verification. |
| `litellm.base-url` | `AGENT_DEFINITIONS_LITELLM_BASE_URL` | `http://localhost:4000` | Proxy root (without `/v1`). |
| `litellm.api-key` | `LITELLM_AGENT_REGISTRY_KEY` | – | Key for `GET /v1/agents` and `POST /policies/resolve`. Never in YAML. |
| `litellm.connect-timeout` / `read-timeout` | `…_CONNECT_TIMEOUT` / `…_READ_TIMEOUT` | `3s` / `10s` | |
| `litellm.gateway-guardrails.<id>` | – | – | Guardrail id → LiteLLM guardrail name; only active after confirmation via `/policies/resolve`. |
| `litellm.gateway-model-aliases` | – | `[]` | Model aliases routed as their own LiteLLM model (one Embabel LLM service per alias). |
| `litellm.model-gateway-url` / `-key` | `LITELLM_BASE_URL` / `LITELLM_VIRTUAL_KEY` | | OpenAI-compatible gateway for those aliases. |
| `a2a.enabled` | `A2A_ENABLED` | `false` | Inbound A2A endpoint + Agent Card. Requires `enabled`. |
| `a2a.public-base-url` | `A2A_PUBLIC_BASE_URL` | – | URL that appears in the Agent Card. |

Every model alias in a definition must be an Embabel role: `embabel.models.llms.<alias>=<alias>`
(aliases containing a hyphen cannot be set as an env var — use a profile/extra YAML, see §9).

## 3. Local directories

```
agent-definitions/
  agents/customer-support.md          # YAML frontmatter (id, version, description, model, skills,
                                      # optionalSkills, tools, guardrails) + Markdown instructions
  skills/customer-lookup/SKILL.md     # Agent Skills format: name (= directory name), description, …
  skills/customer-lookup/references/  # text resources (.md/.txt/.json/.yaml/.yml/.csv)
```

Rules: ids are kebab-case (max 64); unknown or duplicate frontmatter keys, symlinks, `..`, files > 256 KB
and skill packages > 1 MB are refused. `scripts/` is never read or executed.
`allowed-tools` in a skill and text in instructions **never** grant tools.

## 4. PostgreSQL mapping

Migration `src/main/resources/db/migration/V3__create_agent_tenant_bindings.sql` was approved
(2026-10-14) and runs with the regular Flyway migrations on every database.
Local seed (docker-compose only, via `SPRING_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed-local`):
`db/seed-local/R__seed_local_agent_bindings.sql` binds the 4 seeded agents to tenant `local`
(model `agent-chat`, guardrail `pii-protection`, no tools); insert-if-missing, changes are preserved.
Note: once the seed has been applied, Flyway expects the location `db/seed-local` on every start of that
database (otherwise validation fails on a "missing" repeatable migration).

| Table | Key | Content |
|---|---|---|
| `agent_tenant_bindings` | `(tenant_id, agent_id)` | `enabled`, `model_alias` (pin), `pinned_version`, `fallback_allowed`, `fallback_on_not_found` |
| `agent_tenant_binding_tools` | `(tenant_id, agent_id, tool_id)` | tool allowlist |
| `agent_tenant_binding_guardrails` | `(tenant_id, agent_id, guardrail_id)` | additional mandatory guardrails |

Example: `docs/agent-integration/demo-seed.sql`.

## 5. Fallback matrix

| Remote status | `fallback_allowed` | Config | Result |
|---|---|---|---|
| FOUND | – | – | remote (and stored as last-known-good) |
| UNSUPPORTED | – | – | next source (the source does not exist) |
| UNAVAILABLE | yes | `on-unavailable=true` | last-known-good → local |
| UNAVAILABLE | no, or config off | – | error `UNAVAILABLE` |
| NOT_FOUND | yes + `fallback_on_not_found` | `on-not-found=true` | last-known-good → local |
| NOT_FOUND | otherwise | – | error `NOT_FOUND` |
| UNAUTHORIZED / INVALID | – | – | always an error, never a fallback |

Last-known-good: expired → skipped + deleted; corrupt/hash mismatch → skipped + deleted.
Every selected definition goes through the same validation (binding, tools, guardrails, model) against the
**current** state; a snapshot therefore cannot bring back revoked permissions.
Local definitions are a fallback for **configuration**, not for an unreachable model gateway.

## 6. Guardrails

| Id | Executor | Coverage | Fail-closed |
|---|---|---|---|
| `pii-protection` | `PiiProtectionGuardrail` (Embabel `UserInputGuardRail` + `AssistantMessageGuardRail`, CRITICAL) | LLM input and output of the run | yes: Embabel throws `GuardRailViolationException` → A2A error `GUARDRAIL_VIOLATION` |
| `<gateway-id>` (config) | `LiteLlmPolicyGuardrailExecutor` | LLM calls to the model alias via LiteLLM | yes: only operational when `POST /policies/resolve {"model": alias}` lists the LiteLLM guardrail in `effective_guardrails` |
| tool access | `ToolAccessPolicy` (bind time) + `SnapshotBoundTool` → `OpaToolCallAuthorizer` (call time) | every tool call | yes; OPA off = allowlist only |

A guardrail id without an operational executor blocks the run (`GUARDRAIL_MISSING`). A policy ID
that appears only in a prompt or YAML without an executor does not count.
LiteLLM side (`infrastructure/litellm/config.yaml`): guardrail `openjcockpit-pii`
(`litellm_content_filter`, `nl_bsn_contextual`/`iban`/`credit_card`, BLOCK, pre+post), policy
`openjcockpit-agent-pii` attached to model `customer-chat`; for A2A traffic via
`litellm_params.guardrails` in the agent registration.

## 7. A2A registration with LiteLLM

Files: `infrastructure/litellm/a2a/customer-support.agent.yaml` (config) and `.json` (API payload).
Pinned protocol version: **A2A 0.3** (`protocolVersion: "0.3.0"`, transport JSONRPC). Supported:
`message/send` (synchronous, result = `Message`). Not supported (explicit error): `message/stream`
(-32004), `tasks/*` (-32001), push notifications (-32004).

Options (both require approval for a shared/live instance):
1. Merge the `agents:` block into `infrastructure/litellm/config.yaml` and restart LiteLLM.
2. `curl -X POST $LITELLM_URL/v1/agents -H "Authorization: Bearer $LITELLM_MASTER_KEY" -H 'Content-Type: application/json' -d @infrastructure/litellm/a2a/customer-support.agent.json`

LiteLLM requirements: v1.101.2 (pinned in `docker-compose.yml`), a2a-sdk present in the image, and the
client key must have access to the agent. The caller sends its Keycloak JWT as
`x-a2a-customer-support-authorization: Bearer <jwt>`; LiteLLM forwards it as `authorization`.

## 8. Health checks

- `GET /actuator/health` (public) — existing; `readiness` = `readinessState,db`.
- `GET /actuator/health/agentDefinitions` — local definition root readable, number of agents/skills.
  Deliberately not part of readiness: remote outages are absorbed by the fallback.

## 9. Local stack: `docker compose up --build`

One command, everything seeded locally:

```bash
docker compose up -d --build
./scripts/a2a-roundtrip.sh        # calls the 4 seeded agents via the LiteLLM A2A Gateway (:4100)
```

**One seed service** `local-seed` (`infrastructure/local-seed/seed-local.sh`; runs on every `up`,
idempotent, no compose `--profile` needed):

| Part | Script | Result |
|---|---|---|
| Keycloak A2A client | `keycloak-a2a-client.sh` | confidential client `openjcockpit-a2a` (client_credentials; token contains `tenant_id=local`), secret `A2A_CLIENT_SECRET` |
| LiteLLM model key | `litellm-keys.sh` | `embabel-agent-runtime` (`LITELLM_VIRTUAL_KEY`), incl. `agent-chat`, `customer-chat` |
| LiteLLM MCP-only key | `litellm-keys.sh` | `embabel-agent-service-mcp` (`LITELLM_AGENT_SERVICE_KEY`): git MCP server only, for checkpoint commits |
| **The** LiteLLM A2A key | `litellm-keys.sh` | `openjcockpit-a2a` (`LITELLM_A2A_KEY`): `allowed_routes: ["/v1/agents","agent_inference_routes"]`, grants for the 4 agents |
| Cleanup | `litellm-keys.sh` | deletes keys from earlier setups (`embabel-agent-catalog`, `openjcockpit-a2a-client`, `embabel-agent-service`) |

**One Spring profile** `local` (`application-local.yml`, activated by compose with
`SPRING_PROFILES_ACTIVE=local`): Flyway incl. `db/seed-local` (tenant `local` bindings), agent definitions +
A2A on, model aliases, the agent catalogue and the A2A provider. Compose only passes container paths and secrets.

**One A2A client per A2A provider.** `openjcockpit.a2a-providers` (a list; locally one entry `type: litellm`)
→ `A2AProviderClients` creates exactly one `A2AProviderClient` per provider (`LiteLlmA2AProviderClient`):
`listAgents()` (catalogue), `agentCard(name)` (Details), `sendMessage(name, text, callerAuthorization)`
(A2A `message/send` via the gateway; the caller JWT is passed along as `x-a2a-<agent>-authorization`). The same
client provides the LiteLLM connection for gateway guardrail checks. There are no other LiteLLM/A2A HTTP clients
any more; the separate `agent-catalog.litellm` and `agent-definitions.litellm` connections have been removed
(`agent-definitions.litellm` now only contains model gateway settings).

Calling it manually: get a token via
`curl -d grant_type=client_credentials -d client_id=openjcockpit-a2a -d client_secret=openjcockpit-a2a-local-secret http://localhost:8080/realms/openjcockpit/protocol/openid-connect/token`
and `POST http://localhost:4100/a2a/<agent>` with `x-litellm-api-key: Bearer sk-openjcockpit-a2a-local` and
`x-a2a-<agent>-authorization: Bearer <token>`.

## 9a. Agent catalogue in the dashboard (Workflow design → Agents)

`GET /api/agent-catalog` (embabel-agent-service, relayed by ai-control-service) returns:

- **Local** — all `*.md` agents under the repository directory `/agents` (recursive; subdirectories = hierarchy).
  Config: `openjcockpit.agent-catalog.local-path` (profile `local`: `/agents`; compose mounts `./agents:/agents:ro`).
- **One section per A2A provider client**, headed by the provider name (locally **LiteLLM**). The Endpoint column
  shows the gateway URL for users (`LITELLM_PUBLIC_URL`, default `http://localhost:4100`); "routes to"
  is the internal address LiteLLM forwards to. **Details** fetches the agent card hosted by LiteLLM live.

In the LiteLLM registration (`config.yaml`) the `url` deliberately stays the Docker-internal address
`http://embabel-agent-runtime:8082/a2a/<id>` (that is where LiteLLM forwards to). Config agents are read from
`config.yaml` when LiteLLM starts; after changes `docker compose restart litellm` is enough.

### Managing A2A services (Workflow designer → Configuration)

The **A2A services** block shows all providers with live status and number of agents (`GET /api/a2a-services`):

- **From configuration** (`openjcockpit.a2a-providers`, locally LiteLLM) — read-only; changing or deleting returns 409.
- **Added in the dashboard** — LiteLLM-compatible gateways (`/v1/agents`, `/a2a/<name>`), stored in
  table `a2a_services` (V6). Fields: id (fixed, `a2a:<id>:<agent>` in workflows), name, base URL, public URL, API key
  and optionally a caller identity (token URL, client id, client secret; embabel uses it to obtain a
  client-credentials token that the gateway passes on to the agents). A change takes effect immediately in the
  catalogue and in workflow runs, without a restart.
- Secrets are stored encrypted (AES-GCM) with `A2A_SERVICE_ENCRYPTION_SECRET` (min. 16 characters) and
  `A2A_SERVICE_ENCRYPTION_SALT` (hex); the API never returns them, only "set". Without a key nothing can be
  stored (503) and stored services are unusable (`NOT_CONFIGURED`); a different key makes the
  stored secrets unreadable — re-enter them in that case. Locally, compose sets a fixed test key.
- A service cannot be deleted while a workflow uses one of its agents (409 with the workflow ids).
- Gateway guardrails and policy checks only use the LiteLLM provider from configuration.

## 9b. Workflows consist of references to catalogue agents

A workflow only refers to agents from the central agent catalogue (Workflow design → Agents);
the built-in pipeline (requirement … evidence, realisation, spec publication) has been removed. The workflow is
the only execution engine: every run starts via a workflow (`POST /api/workflows/{id}/start`) or a
git-workspace job that starts a workflow.

- **Reference:** `local:<agent-id>` (repository directory `/agents`) or `a2a:<provider>:<agent>` (e.g.
  `a2a:litellm:qa-engineer`). In the API/YAML DTO the field is still called `agentIds`.
- **Link table** `workflow_agent_refs` (Flyway V4): `workflow_id`, `position` (execution order), `source`
  (LOCAL/A2A), `provider`, `agent_id`. The single source of truth; workflow YAML no longer contains agents.
  Pre-catalogue YAML with stage ids loads without agents and must be re-linked in the designer.
- **Validation on save:** only valid references, each at most once; a local agent must be valid in
  `/agents`, an A2A agent must exist at a configured provider (if the provider is unreachable, this is
  checked again at start). Import only checks the syntax.
- **Execution** (`EmbabelOrchestrator.pipeline`): the agents run sequentially; each agent receives
  the workflow input + the outputs of the preceding agents (budget `openjcockpit.agent-workflows.max-message-chars`,
  default 30 000; the oldest outputs are dropped first). Every output is recorded as a run event (first 1 900 characters).
  - Local: executed by `embabel-agent-runtime` (tenant binding, tool allowlist, guardrails,
    model alias), definitions from `/agents` sent as a snapshot. Tenant: `openjcockpit.agent-workflows.tenant`
    (profile `local`: `local`), otherwise the workflow's customer.
  - A2A: via the provider's single `A2AProviderClient` (`message/send`), authenticated with the
    provider's caller identity (`a2a-providers[].caller-auth`, client credentials; locally the Keycloak client
    `openjcockpit-a2a` with `tenant_id=local`).
- **Approval gates and workflow orbs** anchor to an agent reference. Accept with comments re-runs that agent
  with the feedback and its previous output (max. 3 iterations).
- **Dashboard:** the workflow editor picks agents per catalogue section (Local / LiteLLM) and shows the
  execution order with ↑/↓; gate and orb dropdowns show the linked agents in that order.
- **Default workflows** (`default-workflows.json`) link the seeded LiteLLM agents; they do not push to
  git themselves; publishing to git goes through git-workspace jobs (see below).
- **Full outputs** per agent are stored in `agent_run_outputs` (Flyway V5, max. 1 MB per agent) and can be
  retrieved via `GET /api/agent-runs/{runId}/outputs`.
- **Git-workspace jobs** (ai-control-service, `POST /api/agentic-workflows/start`) require a `workflowId`.
  Preflight refuses (409) an unknown workflow (`WORKFLOW_NOT_FOUND`) or a workflow without linked agents
  (`WORKFLOW_HAS_NO_AGENTS`). Column `git_workspace_jobs.workflow_id` (ai-control V13). Flow:
  1. **Clone + branch** in the git-mcp-server job workspace `job-<job-id>`, via LiteLLM
     (`git_create_branch`, key `ai-control-service`).
  2. **Workflow** starts in embabel with ai-control's own service identity (Keycloak client
     `openjcockpit-ai-control`) and a **git-workspace ticket** for ai-control itself (see below); embabel
     binds the run to the workspace (`WorkflowRunScope.gitWorkspace`).
  3. **Agents** get the git tools `git.list_files`, `git.read_file` (all four) and `git.write_file`
     (software-developer, qa-engineer), via the definition (`tools:`) and the tenant binding (`agent_tenant_binding_tools`).
     The tools use the run's repository and workspace, never LLM input, and go through LiteLLM
     (key `embabel-agent-runtime`). A2A agents only receive the run id (`x-a2a-<agent>-x-openjcockpit-workflow-run`);
     the inbound A2A adapter looks up the workspace, and only for calls from `azp=openjcockpit-a2a`.
     Without a bound workspace the tools say so (starts without a project, orb child workflows).
  4. **Checkpoint after every agent** (embabel, `GitWorkspaceCheckpoints`): the full output is written into the
     workspace as `agent-output/<run-id>/<NN>-<agent>.md`, followed by `git_commit` ("checkpoint n/N: <agent>")
     and `git_push` of the run branch, before the next agent starts. A failed checkpoint is a run event,
     not an error. Can be turned off with `GIT_WORKSPACE_CHECKPOINTS=false`.
  5. **Final commit + push** by ai-control when the run is finished. "Nothing to commit" is normal (everything was
     already in the checkpoints); the branch is pushed anyway, so a failed checkpoint push is retried here.
     A run that does not end `COMPLETED` → job `FAILED`, without a final push; the pushed checkpoints remain.
  The final status (`COMPLETED`/`FAILED`) survives cleanup (cleanup is an event, not a status).
- **Dashboard starts on a project run in a git workspace** (`ProjectWorkflowRunService`): if the start
  has a `projectId` with a repository, then
  1. ai-control creates a job, synchronously (before the first agent) clones the repository into `job-<job-id>` and creates
     branch `<branch-prefix>/<workflow>/<yyyyMMdd-HHmmss>-<job>` (one branch per run; prefix
     `openjcockpit.agentic-workflow.branch-prefix`, default `agentic`);
  2. ai-control (service identity) has embabel issue a **ticket** for that workspace, this workflow and the
     user's `sub`;
  3. it starts the run with the **user's own token** + the ticket: OPA (`workflow.start`, agents) evaluates the
     user and the run is registered in their name;
  4. checkpoints after every agent and the final push by ai-control follow (as for jobs). All agents of the run
     share the same workspace: the developer reads what the analyst and architect wrote.
  The start response reports the branch. Clone fails → 502 and no run; blocked start → job `FAILED`.
  Starts without a project (or a project without a repository) run as before, without a workspace.
- **Git-workspace tickets** (embabel, `POST /internal/git-workspace-tickets`, `GitWorkspaceTickets`): only
  `azp=openjcockpit-ai-control` may issue them (otherwise 403). A ticket belongs to one workspace+branch, one
  workflow and one `sub` (omitted → ai-control itself, for jobs), is valid for 2 minutes
  (`GIT_WORKSPACE_TICKET_TTL`), and is consumed on the first redemption attempt, even if that attempt does not match.
  Starting with a ticket that does not match → `BLOCKED`. Max. 1 000 outstanding tickets, in memory only, never logged.
  A `gitWorkspaceTicket` sent by the caller is removed by ai-control. The token relay in
  ai-control leaves an explicit service `Authorization` untouched (otherwise a service call made during a
  user request would send both tokens).
- **Verified live (2026-10-15, `docker compose up --build`):** clone/branch via LiteLLM in `job-<id>`,
  credentials supplied by ai-control to the git-mcp-server, run bound to the workspace, agents call
  `git_list_files`/`git_read_file` without a policy refusal. Fixed along the way:
  - Keycloak 26 ignored `KC_HOSTNAME_URL` (hostname v1): tokens obtained inside the compose network
    had `iss=http://keycloak:8080/…` and were refused by all services (401 on the agent card,
    so every A2A call via LiteLLM). Now `KC_HOSTNAME=http://localhost:8080` + `KC_HOSTNAME_BACKCHANNEL_DYNAMIC`.
  - OPA refused every tool call of configuration-driven agents (no `workflowId`). Now **every**
    workflow run (including dashboard starts without a git workspace) passes a `WorkflowRunScope` (workflow id, run id,
    optional git workspace) to its agents: locally by run id in the execution request, A2A via the run id only
    (`x-a2a-<agent>-x-openjcockpit-workflow-run`, looked up in `WorkflowRunScopes`, only from `azp=openjcockpit-a2a`).
    OPA receives the workflow run's `workflowId` and `workflowExecutionId`. Agent runs outside a workflow
    (e.g. a standalone A2A call) have no workflow and remain refused.
  - Stub spec repository: `listSpecs("*")` found nothing, so jobs never found a spec.
  - Job poll: `RUN_STATE_LOST` is terminal; on a timeout the job also stops the run.
  - Locally `WORKFLOW_AGENT_TIMEOUT=3600` (four agents on the local model take >10 min).
  Existing and not fixed: the job container image is not pulled automatically
  (`docker pull eclipse-temurin:25-jre`), and a job running during a restart of ai-control stays in
  `AGENTS_RUNNING`.
- **Selected spec as agent input (dashboard starts):** the dashboard only sends the file name of the
  selected spec. ai-control (`WorkflowStartEnrichmentService`) reads that spec from the `specs/` directory of the
  project repository (`ProjectSpecService.readSpecContent`) and passes `# Spec: specs/<name>` + the content on as
  workflow input. Agents therefore need no git tools or workspace to read the spec. Unknown name
  or unreadable repo: the name is passed through unchanged (the start does not fail). The input counts towards the
  message budget (`max-message-chars`, input max. 1/3 = 10 000 characters with the default).
- **Git credentials never go through LiteLLM or an LLM.** No git tool has a `username`/`token` argument any more.
  The git-mcp-server fetches a workspace's credentials itself from ai-control
  (`GET /internal/git-workspaces/{key}/credentials?repositoryUrl=…`, Keycloak client `openjcockpit-git-mcp`):
  only for that client (`azp`, otherwise 403), only while the job is running and only for the job's project
  repository (otherwise 404); `Cache-Control: no-store`, never logged. Not part of the public contract and not via
  the dashboard proxy (`/api/` only). A workspace without a key is anonymous; a key without
  credentials fails closed.
- **Removed (2026-10-15):** `GET /api/agents` and `POST /api/agent-runs` (embabel as well as the relay in ai-control,
  contract `AgentDefinitionDto`/`AgentRunRequestDto`), the Requirement…Evidence agents, subagents, realisation,
  spec git publication and the config blocks `openjcockpit.agent-pipeline` and `openjcockpit.spec-git`
  (`SPEC_GIT_*` env in compose). Kept: `GET`/`DELETE /api/agent-runs/{runId}` and `…/outputs`.
  `/api/agent-definitions` now only returns stored definitions (`builtIn` is always `false`).
- **git-mcp-server** is a separate MCP server, registered with LiteLLM (`config.yaml` →
  `mcp_servers.openjcockpit_git`, SSE `http://git-mcp-server:8093/sse`, fixed `server_id`,
  `allow_all_keys: false`, private network only) and **no longer published on the host** (it has no
  authentication of its own, so it is reachable only via LiteLLM). `local-seed` grants `object_permission.mcp_servers: [openjcockpit_git]`
  to `embabel-agent-runtime` (agent tools), `embabel-agent-service-mcp` (checkpoint commits) and
  `ai-control-service` (jobs; only `/mcp-rest/tools/call|list`, no models). The old, direct MCP client in
  embabel (`mcp/*`, `GIT_MCP_SERVER_URL`) is disabled.
- **Git credentials in `WorkflowStartInput`/`AgentRunRequest`** (`gitUsername`/`gitToken`) remain for
  dashboard starts; jobs do not use them (see above).
- **Known limitations of workspace runs:** the job follows the run for at most `WORKFLOW_AGENT_TIMEOUT` (locally 3600 s),
  including while an approval gate is waiting, and stops the run afterwards; tickets and run scopes are in memory (a restart of
  embabel → the running binding is lost); orb child workflows do not inherit the workspace.

## 10. Test commands

```bash
# everything (from the repo root)
mvn -o -pl services/embabel-agent-service,services/embabel-agent-runtime -am test
# SPEC-002 only (the runtime now owns definitions and inbound A2A)
mvn -o -pl services/embabel-agent-runtime -am test -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest='nl.metafactory.agentruntime.definition.**.*Test,nl.metafactory.agentruntime.a2a.*Test'
# live roundtrip (requires a running stack, see §9)
LITELLM_KEY=… A2A_JWT=… ./scripts/a2a-roundtrip.sh
```

## 11. Limitations

- LiteLLM v1.101.2 has no API for agent Markdown or skill package content: remote content = `UNSUPPORTED`; the local source provides the content.
- A2A: 0.3 only, `message/send` only, no streaming/tasks/push; no authenticated extended card.
- The tenant claim only exists for the local service client `openjcockpit-a2a` (hard-coded `tenant_id=local`); real users/tenants still need a Keycloak mapper per tenant. A changed tenant value in an existing mapper is not overwritten by the seed.
- `logging.level.com.embabel.agent: DEBUG` (existing setting) can log prompt content; `INFO` is recommended in production (not changed: it affects existing behaviour).
- `LiteLlmPolicyGuardrailExecutor` proves the attachment configuration via `/policies/resolve`, not the actual enforcement per call; whether the policy engine is available in the LiteLLM edition in use has not been verified live.
- The live roundtrip LiteLLM → Embabel → LiteLLM and the local demo have not been run in this environment (`NOT_RUN_EXTERNAL`).
