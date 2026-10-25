# Agent runtime

`embabel-agent-runtime` is the **execution plane** of OpenJCockpit: the only process that runs agents and
calls LLMs. `embabel-agent-service` is the **management plane**: it decides which agents a workflow runs, in
what order, under which gates and policies, and records the outcome. The two are separate Spring Boot
applications, Maven modules and Docker images that communicate only over the HTTP contract described here.

## Responsibilities

| | `embabel-agent-service` (management) | `embabel-agent-runtime` (execution) |
| --- | --- | --- |
| Workflows | definitions, groups, import/export, orchestration (sequential agent chain), approval gates, workflow orbs, run state and history | — |
| Agents | catalogue (`/agents` folder + A2A providers), workflow agent references, tenant bindings (PostgreSQL) | resolving and running configuration-driven agents (Embabel `ConfiguredDefinitionAgent`) |
| Definitions | `/agents` catalogue folder | served agents and skills (`agent-definitions/`, shipped in the image), last-known-good cache |
| A2A | outbound: provider clients used by workflows and the catalogue | inbound: `/a2a/{agentId}` (JSON-RPC `message/send`) and Agent Cards |
| LLM | — (no LLM credentials) | coding LLM route (LiteLLM / direct Ollama), OpenAI, agent model aliases, definition drafts |
| Tools | — | tool registry, bind-time allowlisting, agent git tools (git MCP via LiteLLM) |
| Policy | OPA client, `mcp.tool.invoke` and all other decisions, decision log export | asks the agent service for every tool call |
| Git workspace | tickets (ai-control), run binding, checkpoint commits | git tools inside the run's workspace |
| Persistence | `embabel` database and its Flyway migrations | none (in-memory execution registry only) |

## The contract

All types live in `services/service-common`, package `nl.metafactory.common.agentruntime`
(`AgentRuntimeApi` lists every path).

### Agent service → runtime (caller identity `openjcockpit-agent-service`)

| Endpoint | Purpose |
| --- | --- |
| `POST /internal/v1/agent-executions` | Run one agent synchronously. Body `AgentExecutionRequest`: `executionId`, `tenantId`, `agentId`, `principal`, `workflowRunId`, `traceparent`, `message`, and for catalogue (`local:`) agents a `definition` snapshot (lookup status + raw Markdown). Answer `AgentExecutionResult` (`text`, agent version, content hash, trace id). |
| `GET /internal/v1/agent-executions/{executionId}` | Lifecycle of an accepted execution (`RUNNING`, `COMPLETED`, `FAILED`); never the output. |
| `POST /internal/v1/definition-drafts/{agent\|subagent\|skill}` | LLM draft for the workflow designer; the agent service sanitises it. |

Failures are `AgentExecutionFailure` bodies with a stable `reason`: `INVALID_REQUEST` (400), `FORBIDDEN`
(403), `UNKNOWN_EXECUTION` (404), `DUPLICATE_EXECUTION` (409), a resolution failure such as
`TENANT_FORBIDDEN`, `NOT_FOUND`, `INVALID`, `TOOL_DENIED`, `GUARDRAIL_MISSING` or `GUARDRAIL_VIOLATION`
(422), `UNAVAILABLE` (503), `EXECUTION_FAILED` (500). Messages never contain prompts, outputs or secrets.

### Runtime → agent service (caller identity `openjcockpit-agent-runtime`)

| Endpoint | Purpose |
| --- | --- |
| `GET /internal/agent-runtime/v1/tenants/{tenantId}/agent-bindings/{agentId}` | The tenant's binding (enabled, pins, tool allowlist, required guardrails); 404 when none. |
| `GET /internal/agent-runtime/v1/workflow-runs/{runId}` | Scope of an active run (workflow id, git workspace); 404 when unknown or finished. |
| `POST /internal/agent-runtime/v1/tool-invocation-decisions` | OPA decision (`mcp.tool.invoke`) for one tool call, written to the agent service's decision log. |

## How agents reach the runtime

- **`local:<agent>` references** (repository `/agents` folder): the workflow engine reads the file, sends it
  as a definition snapshot with the run id, and waits for the answer. The runtime parses and validates the
  Markdown itself; resolution order and failure codes are those of a local source (binding first, no
  fallback).
- **`a2a:<provider>:<agent>` references**: unchanged — the engine calls the agent through the provider (locally
  the LiteLLM A2A gateway) with its A2A identity and only the run id in a forwarded header. LiteLLM routes
  the seeded agents to the runtime's `/a2a/{agentId}`.
- **External A2A callers** (scripts, other agents) call LiteLLM or the runtime's `/a2a/{agentId}` with their
  own Keycloak token; the tenant comes from its `tenant_id` claim.

## Security model

- **Authentication**: every endpoint of both services except health/info requires a JWT of the
  `openjcockpit` realm (same issuer and JWKS as before). Network location grants nothing.
- **Authorisation of internal endpoints**: by the caller's Keycloak client (`azp`) — `ServiceCallers` in
  `service-common`. Wrong or missing client → 403. A user token can never reach either internal API.
- **Tenant isolation**: tenant bindings stay the server-side authority. The runtime resolves every run
  against the binding the agent service reports; a definition (catalogue snapshot or local file) can only
  narrow what the binding grants.
- **Workflow scope**: callers only ever name a run id. The runtime asks the workflow engine for the run's
  workflow and git workspace and validates them (`GitWorkspaceBinding`) before any tool sees them. Inbound
  A2A honours the run id only from the engine's A2A identity (`engine-a2a-client-id`), as before.
- **Policy**: the agent service remains the single policy decision point (OPA configuration is editable at
  runtime in its UI, and its decision log is the audit trail). The runtime asks per tool call and **denies**
  when no decision can be obtained.
- **Fail closed**: no agent service → no binding → `UNAVAILABLE`; no run scope → the execution fails
  (direct) or the agent runs without workflow (A2A), so its tool calls are refused; no service identity
  configured → no internal request is sent at all.
- **Secrets**: LLM keys (`OPENAI_API_KEY`, `LITELLM_VIRTUAL_KEY`) exist only in the runtime; the agent
  service holds an MCP-only LiteLLM key for checkpoint commits; each side holds only its own Keycloak client
  secret.

## Long-running executions, timeouts and duplicates

An execution is one synchronous request, exactly like the in-process call it replaces: the workflow
engine's pipeline thread waits (default `openjcockpit.agent-runtime.execution-timeout: 60m`). The client
never retries. Each invocation gets a unique `executionId` (`<runId>:<uuid>`); the runtime accepts an id
once and refuses a second request with `409 DUPLICATE_EXECUTION`, so a proxy retry or a resent request can
never run an agent twice. When the agent service gets no answer it asks
`GET /internal/v1/agent-executions/{id}` and reports whether the agent is still running — it does not resend.

Cancellation keeps its existing semantics: stopping a workflow run takes effect between agents; an agent
that is already running finishes (there was no mid-agent cancellation before either). Streaming is not
supported (A2A `message/stream` is rejected explicitly, as before).

## Operations

- Health: `GET /actuator/health/readiness` (runtime has no database; dependencies are checked per request).
- Logs carry `runId`, `traceId`, `tenantId`, `agentId`, `agentVersion` in the MDC; message content is never logged.
- Image: `openjcockpit/embabel-agent-runtime`, built from `services/embabel-agent-runtime/Dockerfile`,
  unprivileged user, served definitions in `/agent-definitions`.

## Related

- [Architecture](architecture.md)
- [Remote agent runtime](../operations/remote-agent-runtime.md)
- [A2A agents](../integrations/a2a-agents.md)
- [Configuration reference](../reference/configuration-reference.md)
