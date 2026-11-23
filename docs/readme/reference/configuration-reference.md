# Configuration reference

An index of the environment variables you can set for the local Docker Compose stack, with their
defaults and the page that explains them. Topic pages are the authoritative description; this page
helps you find them.

- **Compose defaults** come from `${VAR:-default}` in [`docker-compose.yml`](../../../docker-compose.yml).
  Values you put in `.env` override them (Compose reads `.env` for interpolation).
- **Application defaults** come from each service's `application.yml` and apply when a variable is not
  passed into the container (for example when running a service on the host).
- Only variables that `docker-compose.yml` passes into a container's `environment` reach that container.
  See the [Revert A/B verification note](../integrations/llm-providers.md#reverting-the-default-llm).

> All secret-like defaults below are **local-only, publicly known placeholders**. Replace them in any
> shared environment. See [Security considerations](../governance/security-considerations.md).

## Required

| Variable | Default | Used by | Docs |
| --- | --- | --- | --- |
| `OPENAI_API_KEY` | none (placeholder in `.env.example`) | `ai-control-service`, `embabel-agent-runtime` | [LLM providers](../integrations/llm-providers.md) |

## LLM, Ollama and LiteLLM

| Variable | Compose default | Docs |
| --- | --- | --- |
| `OLLAMA_BASE_URL` | `http://host.docker.internal:11434` (host-run: `http://localhost:11434`) | [LLM providers](../integrations/llm-providers.md#local-ollama-qwen-model-prerequisite-before-you-start-the-stack) |
| `OLLAMA_MODEL` | `qwen3.6:27b` | [LLM providers](../integrations/llm-providers.md#local-ollama-qwen-model-prerequisite-before-you-start-the-stack) |
| `LITELLM_MASTER_KEY` | `sk-openjcockpit-litellm-local` | [LLM providers](../integrations/llm-providers.md#litellm-gateway) |
| `LITELLM_SALT_KEY` | `sk-openjcockpit-litellm-salt-local` | [LLM providers](../integrations/llm-providers.md#litellm-gateway) |
| `LITELLM_VIRTUAL_KEY` | `sk-openjcockpit-embabel-local` | [LLM providers](../integrations/llm-providers.md#litellm-gateway) |
| `LITELLM_A2A_KEY` | `sk-openjcockpit-a2a-local` | [A2A agents](../integrations/a2a-agents.md) |
| `LITELLM_AI_CONTROL_KEY` | `sk-openjcockpit-ai-control-local` | [MCP tools](../integrations/mcp-tools.md#how-it-is-reached) |
| `LITELLM_UI_USERNAME` / `LITELLM_UI_PASSWORD` | `ricky` / `Welkom01!` | [LLM providers](../integrations/llm-providers.md#litellm-gateway) |
| `LITELLM_PUBLIC_URL` | `http://localhost:4100` | [A2A agents](../integrations/a2a-agents.md#the-agent-catalogue) |
| `AI_PROVIDER` | `LiteLLM` | [Choosing the AI provider](../integrations/llm-providers.md#choosing-the-ai-provider) |
| `AI_MODEL` | unset (required for `OpenAI` / `Anthropic`) | [Choosing the AI provider](../integrations/llm-providers.md#choosing-the-ai-provider) |
| `ANTHROPIC_API_KEY` | unset (required for `Anthropic`) | [Choosing the AI provider](../integrations/llm-providers.md#choosing-the-ai-provider) |
| `LLM_CODING_ROUTE` | unset → `gateway` (application default) | [Revert B](../integrations/llm-providers.md#reverting-the-default-llm) |
| `EMBABEL_MODELS_DEFAULT_LLM` | unset | [Revert A](../integrations/llm-providers.md#reverting-the-default-llm) |

## Identity and service clients

The Keycloak instance itself (URL, realm, client ids, or none at all) is set under
[Keycloak (backend services)](#keycloak-backend-services).

| Variable | Compose default | Docs |
| --- | --- | --- |
| `A2A_CLIENT_SECRET` | `openjcockpit-a2a-local-secret` | [Authentication](../getting-started/authentication.md#service-identities) |
| `AI_CONTROL_CLIENT_SECRET` | `openjcockpit-ai-control-local-secret` | [Authentication](../getting-started/authentication.md#service-identities) |
| `GIT_MCP_CLIENT_SECRET` | `openjcockpit-git-mcp-local-secret` | [Authentication](../getting-started/authentication.md#service-identities) |
| `LITELLM_GIT_MCP_CLIENT_SECRET` | `openjcockpit-litellm-git-local-secret` | Keycloak client `openjcockpit-litellm-git`: LiteLLM's token for `git-mcp-server`. Required in production. |
| `GIT_MCP_KEYCLOAK_ENABLED` | `false` (production Compose/Kubernetes: always `true`) | `KEYCLOAK_ENABLED` of `git-mcp-server`. [git-mcp-server](../../../services/git-mcp-server/README.md) |
| `KEYCLOAK_ENABLED`, `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWK_SET_URI`, `KEYCLOAK_EXPECTED_AUDIENCE`, `KEYCLOAK_ALLOWED_AZP` (git-mcp-server) | `false` (`prod` profile: `true`), blank | Incoming MCP authentication. Production Compose overrides: `GIT_MCP_KEYCLOAK_ISSUER_URI`, `GIT_MCP_KEYCLOAK_JWK_SET_URI`, `GIT_MCP_KEYCLOAK_AUDIENCE`, `GIT_MCP_KEYCLOAK_ALLOWED_AZP`. [git-mcp-server](../../../services/git-mcp-server/README.md) |
| `GIT_MCP_SERVER_TOKEN_URL`, `GIT_MCP_SERVER_CLIENT_ID`, `GIT_MCP_SERVER_CLIENT_SECRET` | Compose: the Keycloak token URL; application defaults: blank, `openjcockpit-agent-service`, `AGENT_SERVICE_CLIENT_SECRET` (`prod`: `KEYCLOAK_TOKEN_URL`) | Token that embabel-agent-service's MCP client sends to `git-mcp-server`. Blank token URL: no token. |
| `GIT_MCP_TLS_CERT`, `GIT_MCP_TLS_KEY`, `GIT_MCP_TLS_KEY_PASSWORD`, `GIT_MCP_TLS_CA` | blank (HTTP) | Production Compose/Kubernetes: HTTPS on `git-mcp-server`; passed to the image entrypoint as `TLS_CERT`/`TLS_KEY`/`TLS_KEY_PASSWORD`/`TLS_CA` (PEM or base64 PEM). With TLS on, also set `GIT_MCP_SERVER_URL` to `https://…` and `EMBABEL_AGENT_SERVICE_TLS_CA` for a private CA. [git-mcp-server](../../../services/git-mcp-server/README.md#14-tls-https) |

## Encryption

| Variable | Compose default | Docs |
| --- | --- | --- |
| `ENCRYPTION_SECRET` / `ENCRYPTION_SALT` | `prod-change-this-32chars-minimum!` / `0123456789abcdef` | [Git integration](../workflows/git-integration.md#project-setup) |
| `A2A_SERVICE_ENCRYPTION_SECRET` / `A2A_SERVICE_ENCRYPTION_SALT` | `openjcockpit-a2a-services-local-secret` / `a2a5e7c1ce5a17ed` | [A2A agents](../integrations/a2a-agents.md#the-agent-catalogue) |

## Workflows, approval gates and chaining

| Variable | Compose default | Docs |
| --- | --- | --- |
| `APPROVAL_GATE_MAX_FEEDBACK_ITERATIONS` | `3` | [Approval gates](../governance/approval-gates.md#the-three-tunables) |
| `APPROVAL_GATE_COMMENT_MAX_LENGTH` | `4000` | [Approval gates](../governance/approval-gates.md#the-three-tunables) |
| `APPROVAL_GATE_MAX_CHANGED_PATHS` | `50` | [Approval gates](../governance/approval-gates.md#the-three-tunables) |
| `WORKFLOW_TRIGGER_SEQUENTIAL_MAX_WAIT` | `30m` | [Workflow chaining](../workflows/agents-and-workflows.md#workflow-chaining-workflow-orbs) |
| `WORKFLOW_TRIGGER_MAX_CHAIN_DEPTH` | `3` | [Workflow chaining](../workflows/agents-and-workflows.md#workflow-chaining-workflow-orbs) |
| `WORKFLOW_TRIGGER_MAX_ORBS_PER_WORKFLOW` | `5` | [Workflow chaining](../workflows/agents-and-workflows.md#workflow-chaining-workflow-orbs) |
| `WORKFLOW_TRIGGER_ENABLED` | `true` | [Workflow chaining](../workflows/agents-and-workflows.md#workflow-chaining-workflow-orbs) |
| `WORKFLOW_AGENT_TIMEOUT` | `3600` (seconds; service default 600) | [Git integration](../workflows/git-integration.md#known-limitations-of-workspace-runs) |
| `WORKFLOW_DEFINITIONS_PATH` | container path on the `workflow-definitions` volume (application default `/tmp/embabel-workflow-definitions`) | [Runbooks](../operations/runbooks.md#local-non-docker-runs-use-a-different-storage-path) |
| `SPEC_WORKFLOW_BASE` | `/tmp/spec-workflow` in the container image (application default `.`); must be writable by UID 10001 | [Spec-driven development](../workflows/spec-driven-development.md#initialize-the-spec-workflow) |
| `SPEC_WORKFLOW_INIT_ENABLED` | `true` (application default) | [Spec-driven development](../workflows/spec-driven-development.md#initialize-the-spec-workflow) |

Application properties without a Compose variable:
`openjcockpit.workflow-definitions.seed-defaults` (default `true`),
`openjcockpit.agent-workflows.max-message-chars` (default 30 000),
`openjcockpit.agentic-workflow.branch-prefix` in `ai-control-service` (env `WORKFLOW_BRANCH_PREFIX`, default `agentic`),
`GIT_WORKSPACE_CHECKPOINTS` and `GIT_WORKSPACE_TICKET_TTL` (see [Git integration](../workflows/git-integration.md)).

## Spec queue

| Variable | Compose default | Docs |
| --- | --- | --- |
| `SPEC_QUEUE_RUNNER_TOKEN_SECRET` | `local-only-spec-queue-runner-secret-CHANGE-ME-outside-local` | [Spec queue](../workflows/spec-queue.md#configuration) |
| `SPEC_QUEUE_RUNNER_ENABLED` | `true` | [Spec queue](../workflows/spec-queue.md#configuration) |
| `SPEC_QUEUE_POLL_INTERVAL` | `PT20S` | [Spec queue](../workflows/spec-queue.md#configuration) |
| `SPEC_QUEUE_START_LEASE`, `SPEC_QUEUE_MERGE_LEASE`, `SPEC_QUEUE_MERGE_WAIT_TIMEOUT`, `SPEC_QUEUE_EMBABEL_TIMEOUT`, `SPEC_QUEUE_GITHUB_TIMEOUT` | application defaults only | [Spec queue](../workflows/spec-queue.md#configuration) |

## Policy and tools

| Variable | Default | Docs |
| --- | --- | --- |
| `OPA_ENABLED` | Compose `true` (application default `false`) | [Policy enforcement](../governance/policy-enforcement.md#policy-client-configuration-openjcockpitopa) |
| `OPA_CONFIG_LOCKED` | `false` (`prod`: always `true`) | Refuses runtime changes through `PUT /api/opa-config`. [Policy enforcement](../governance/policy-enforcement.md#locking-the-policy-client-in-production) |
| `OPA_BASE_URL`, `OPA_POLICY_PATH`, `OPA_HEALTH_PATH`, `OPA_TIMEOUT_SECONDS`, `OPA_FAIL_MODE`, `OPA_DECISION_LOGGING_ENABLED`, `OPA_DECISION_LOG_EXPORT_ENABLED` | see docs | [Policy enforcement](../governance/policy-enforcement.md#policy-client-configuration-openjcockpitopa) |
| `OPA_TOKEN_URL`, `OPA_CLIENT_ID`, `OPA_CLIENT_SECRET` | empty (`prod`: the Keycloak token URL, `openjcockpit-agent-service`, `AGENT_SERVICE_CLIENT_SECRET`) | Keycloak client whose client-credentials token embabel-agent-service sends to OPA. A non-empty `OPA_AUTH_TOKEN` is sent instead. |
| `MCP_CLIENT_ENABLED` | Compose `true` (application default `false`) | [MCP tools](../integrations/mcp-tools.md#central-mcp-client-remotemcptoolexecutor) |
| `GIT_MCP_RUN_WORKSPACE_RETENTION` | `PT24H` | [MCP tools](../integrations/mcp-tools.md#per-run-workspace-isolation-workspacekey) |
| `GIT_MCP_RUN_WORKSPACE_REAP_INTERVAL` | `PT1H` | [MCP tools](../integrations/mcp-tools.md#per-run-workspace-isolation-workspacekey) |

## Workflow containers

| Variable | Compose default | Notes |
| --- | --- | --- |
| `AGENTIC_DOCKER_HOST` | `tcp://docker-socat:2375` | Docker daemon used by `ai-control-service` (compose profile `docker-jobs` only). See [Security considerations](../governance/security-considerations.md#high-impact-local-development-postures). |
| `AGENTIC_CONTAINER_ENABLED` | `false` (`prod`: always off) | Enables workflow containers. Requires the `docker-jobs` profile locally. |
| `AGENTIC_CONTAINER_NETWORK` | `none` | Job container network; `host` and `container:*` are refused. |
| `AGENTIC_CONTAINER_MEMORY_MB`, `AGENTIC_CONTAINER_CPUS`, `AGENTIC_CONTAINER_PIDS_LIMIT` | `512`, `1.0`, `256` | Job container resource limits (no swap beyond memory). |
| `AGENTIC_CONTAINER_IMAGE` | `eclipse-temurin:25-jre` | Not pulled automatically. |

## Production profile

Set by `deploy/` (Spring profile `prod`); see the [Production runbook](../operations/production-runbook.md)
and [`deploy/compose/.env.prod.example`](../../../deploy/compose/.env.prod.example) for the full list.

| Variable | Notes |
| --- | --- |
| `SPRING_PROFILES_ACTIVE=prod` | No fallback values; `ProductionConfigGuard` rejects placeholders, `local` and a non-HTTPS issuer. |
| `MANAGEMENT_PORT` | Actuator port (default `9090` under `prod`); never routed publicly. |
| `KEYCLOAK_ISSUER`, `KEYCLOAK_JWK_SET_URI`, `KEYCLOAK_TOKEN_URL` | Public HTTPS issuer; internal JWKS/token URLs. |
| `AGENT_WORKFLOWS_TENANT` | Tenant of the deployment; seeds its agent bindings (`db/seed-tenant`). |
| `CORS_ALLOWED_ORIGINS` | Empty in production (the dashboard proxies `/api` on its own origin). Locally `http://localhost:5173,http://localhost:4000`. |

## Skills Marketplace

`SKILLS_MARKETPLACE_REQUEST_TIMEOUT_SECONDS`, `SKILLS_MARKETPLACE_CONNECT_TIMEOUT_SECONDS`,
`SKILLS_MARKETPLACE_MAX_RESPONSE_BYTES`, `SKILLS_MARKETPLACE_MAX_ITEMS`,
`SKILLS_MARKETPLACE_MAX_REDIRECTS`, `SKILLS_MARKETPLACE_MAX_CONNECTIONS_PER_FETCH`,
`SKILLS_MARKETPLACE_ALLOW_PRIVATE_EGRESS` (never in a deployed environment): see
[Skills Hub](../integrations/skills-marketplace.md#skills_marketplace_-environment-variables).

## Keycloak (backend services)

`ai-control-service`, `embabel-agent-service` and `embabel-agent-runtime` read the same variables; Compose passes
the same values to all three, each with its own client id.

| Variable | Compose default | Meaning |
| --- | --- | --- |
| `ENABLE_KEYCLOAK` | `true` | `false`: no Keycloak at all. Every request is permitted as one local user (`developer`) holding every role, and the services call each other without tokens; the runtime then needs its `standalone` profile for tenant bindings. Switch it for all three together. Refused with profile `prod`. |
| `KEYCLOAK_URL` / `KEYCLOAK_REALM` | `http://keycloak:8080` / `openjcockpit` | The Keycloak realm; the JWKS, token and (outside `local`) issuer URLs derive from it. |
| `KEYCLOAK_CLIENT_ID` | `AI_CONTROL_SERVICE_CLIENT_ID` / `AGENT_SERVICE_CLIENT_ID` / `AGENT_RUNTIME_CLIENT_ID` | Each service's own client id. |
| `KEYCLOAK_ISSUER` | `http://localhost:8080/realms/<realm>` | The `iss` in tokens (Keycloak's public hostname); set when it differs from `KEYCLOAK_URL`. |
| `KEYCLOAK_JWK_SET_URI`, `AI_CONTROL_SERVICE_TOKEN_URL`, `AGENT_SERVICE_TOKEN_URL`, `AGENT_RUNTIME_TOKEN_URL` | derived | Overrides of the derived JWKS and token URLs. |

The `prod` profile does not derive: it requires `KEYCLOAK_ISSUER`, `KEYCLOAK_JWK_SET_URI` and the token URLs explicitly.

## Agent service ↔ agent runtime

| Variable | Compose default | Used by | Meaning |
| --- | --- | --- | --- |
| `EMBABEL_AGENT_RUNTIME_BASE_URL` | `http://embabel-agent-runtime:8082` | `embabel-agent-service` | Where local agents and definition drafts run. Any host or HTTPS URL. |
| `AGENT_SERVICE_TOKEN_URL` | derived from `KEYCLOAK_URL` / `KEYCLOAK_REALM` | `embabel-agent-service` | Token endpoint of its identity towards the runtime; set only to override. |
| `AGENT_SERVICE_CLIENT_ID` / `AGENT_SERVICE_CLIENT_SECRET` | `openjcockpit-agent-service` / local placeholder | `embabel-agent-service` | Keycloak client the runtime trusts (`openjcockpit.agent-runtime.trusted-client-id` on the runtime). |
| `AGENT_RUNTIME_EXECUTION_TIMEOUT` | `60m` | `embabel-agent-service` | Max wait for one agent execution (never retried). |
| `AGENT_RUNTIME_DRAFT_TIMEOUT` | `10m` | `embabel-agent-service` | Max wait for one definition draft. |
| `EMBABEL_AGENT_SERVICE_BASE_URL` | `http://embabel-agent-service:8081` | `embabel-agent-runtime` | Where bindings, run scopes and tool-call decisions come from. |
| `AGENT_RUNTIME_TOKEN_URL` | Keycloak token endpoint (local profile) | `embabel-agent-runtime` | Token endpoint of its identity towards the agent service. |
| `AGENT_RUNTIME_CLIENT_ID` / `AGENT_RUNTIME_CLIENT_SECRET` | `openjcockpit-agent-runtime` / local placeholder | both | The runtime's identity; the agent service only answers it (`openjcockpit.agent-runtime.runtime-client-id`). |
| `A2A_PUBLIC_BASE_URL` | `http://embabel-agent-runtime:8082` | `embabel-agent-runtime` | Base URL advertised in Agent Cards; LiteLLM agent URLs must match. |
| `LITELLM_VIRTUAL_KEY` | `sk-openjcockpit-embabel-local` | `embabel-agent-runtime` | Model + git MCP key (LiteLLM alias `embabel-agent-runtime`). |
| `LITELLM_AGENT_SERVICE_KEY` | `sk-openjcockpit-agent-service-local` | `embabel-agent-service` | MCP-only key for checkpoint commits (alias `embabel-agent-service-mcp`). |
| `OPENJCOCKPIT_IMAGE_TAG` | `local` | Compose | Tag of `openjcockpit/embabel-agent-service` and `openjcockpit/embabel-agent-runtime`. |

See [Agent runtime](../concepts/agent-runtime.md) and [Remote agent runtime](../operations/remote-agent-runtime.md).

## Agent definitions and A2A

The `openjcockpit.agent-definitions.*` properties (`AGENT_DEFINITIONS_*`, `A2A_ENABLED`,
`A2A_PUBLIC_BASE_URL`, cache and fallback settings) now belong to **`embabel-agent-runtime`** and are
documented in §2 of the
[agent-integration operations guide](../../../services/embabel-agent-service/docs/agent-integration/OPERATIONS.md).
In Compose they are set through the runtime's `local` Spring profile
(`embabel-agent-runtime/src/main/resources/application-local.yml` in the
[OpenJCockpit-Agent-Runtime](https://github.com/OpenJCockpit/OpenJCockpit-Agent-Runtime) repository); the runtime enables agent
definitions by default and ships its definitions in the image (`/agent-definitions`). New:
`LITELLM_REGISTRY_URL` / `LITELLM_REGISTRY_KEY` (the LiteLLM management API, needed only for gateway
guardrails) replace the former reuse of the A2A provider client.

## Dashboard container

The `dashboard` image (nginx, unprivileged) is configured at container start. Variables are substituted
into `/config.json`, the nginx config and the CSP; nothing is baked in at build time except `BUILD_SHA`.

| Variable | Default | Effect |
| --- | --- | --- |
| `KEYCLOAK_URL` | `http://localhost:8080` | Served in `/config.json`; also feeds the CSP `connect-src`, `frame-src` and `form-action`. |
| `KEYCLOAK_REALM` | `openjcockpit` | Served in `/config.json`. |
| `KEYCLOAK_CLIENT_ID` | `openjcockpit` | Served in `/config.json`. |
| `API_UPSTREAM` | `http://ai-control-service:8080` | nginx `proxy_pass` target for `/api/`. |
| `BUILD_SHA` (build arg and env) | `unknown` (`dev` from Compose) | Served at `/version.json`. |

- The container listens on **8080**; Compose maps `4000:8080`.
- `/healthz` is the liveness endpoint and the Docker `HEALTHCHECK`. `/version.json` reports `BUILD_SHA`.
- `/api/` requests may take up to 300 s (`proxy_read_timeout`).
- The image terminates no TLS; the customer ingress does.
- The CSP ships with `style-src 'self' 'unsafe-inline'` because the UI still uses React `style` props.
- If `/config.json` cannot be loaded, the dashboard falls back to Keycloak at `http://localhost:8080`.

## Logging

| Variable | Effect | Docs |
| --- | --- | --- |
| `LOGGING_LEVEL_NL_METAFACTORY_AGENTS_WORKFLOW_YAMLDEFINITIONSTORE` | Log level of definition-file logging | [Runbooks](../operations/runbooks.md#workflow-definition-file-logging) |
