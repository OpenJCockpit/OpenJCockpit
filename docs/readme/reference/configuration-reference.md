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
| `OPENAI_API_KEY` | none (placeholder in `.env.example`) | `ai-control-service`, `embabel-agent-service` | [LLM providers](../integrations/llm-providers.md) |

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
| `LLM_CODING_ROUTE` | unset → `gateway` (application default) | [Revert B](../integrations/llm-providers.md#reverting-the-default-llm) |
| `EMBABEL_MODELS_DEFAULT_LLM` | unset | [Revert A](../integrations/llm-providers.md#reverting-the-default-llm) |

## Identity and service clients

| Variable | Compose default | Docs |
| --- | --- | --- |
| `A2A_CLIENT_SECRET` | `openjcockpit-a2a-local-secret` | [Authentication](../getting-started/authentication.md#service-identities) |
| `AI_CONTROL_CLIENT_SECRET` | `openjcockpit-ai-control-local-secret` | [Authentication](../getting-started/authentication.md#service-identities) |
| `GIT_MCP_CLIENT_SECRET` | `openjcockpit-git-mcp-local-secret` | [Authentication](../getting-started/authentication.md#service-identities) |

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
| `OPA_BASE_URL`, `OPA_POLICY_PATH`, `OPA_HEALTH_PATH`, `OPA_TIMEOUT_SECONDS`, `OPA_FAIL_MODE`, `OPA_DECISION_LOGGING_ENABLED`, `OPA_DECISION_LOG_EXPORT_ENABLED` | see docs | [Policy enforcement](../governance/policy-enforcement.md#policy-client-configuration-openjcockpitopa) |
| `MCP_CLIENT_ENABLED` | Compose `true` (application default `false`) | [MCP tools](../integrations/mcp-tools.md#central-mcp-client-remotemcptoolexecutor) |
| `GIT_MCP_RUN_WORKSPACE_RETENTION` | `PT24H` | [MCP tools](../integrations/mcp-tools.md#per-run-workspace-isolation-workspacekey) |
| `GIT_MCP_RUN_WORKSPACE_REAP_INTERVAL` | `PT1H` | [MCP tools](../integrations/mcp-tools.md#per-run-workspace-isolation-workspacekey) |

## Workflow containers

| Variable | Compose default | Notes |
| --- | --- | --- |
| `AGENTIC_DOCKER_HOST` | `tcp://docker-socat:2375` | Docker daemon used by `ai-control-service`. See [Security considerations](../governance/security-considerations.md#high-impact-local-development-postures). |
| `AGENTIC_CONTAINER_ENABLED` | `true` | Enables workflow containers. |
| `AGENTIC_CONTAINER_IMAGE` | `eclipse-temurin:25-jre` | Not pulled automatically. |

## Skills Marketplace

`SKILLS_MARKETPLACE_REQUEST_TIMEOUT_SECONDS`, `SKILLS_MARKETPLACE_CONNECT_TIMEOUT_SECONDS`,
`SKILLS_MARKETPLACE_MAX_RESPONSE_BYTES`, `SKILLS_MARKETPLACE_MAX_ITEMS`,
`SKILLS_MARKETPLACE_MAX_REDIRECTS`, `SKILLS_MARKETPLACE_MAX_CONNECTIONS_PER_FETCH`,
`SKILLS_MARKETPLACE_ALLOW_PRIVATE_EGRESS` (never in a deployed environment): see
[Skills Hub](../integrations/skills-marketplace.md#skills_marketplace_-environment-variables).

## Agent definitions and A2A

The `openjcockpit.agent-definitions.*` properties (`AGENT_DEFINITIONS_*`, `A2A_ENABLED`,
`A2A_PUBLIC_BASE_URL`, cache and fallback settings) are documented in §2 of the
[agent-integration operations guide](../../../services/embabel-agent-service/docs/agent-integration/OPERATIONS.md).
In Compose they are set through the `local` Spring profile (`application-local.yml`).

## Logging

| Variable | Effect | Docs |
| --- | --- | --- |
| `LOGGING_LEVEL_NL_METAFACTORY_AGENTS_WORKFLOW_YAMLDEFINITIONSTORE` | Log level of definition-file logging | [Runbooks](../operations/runbooks.md#workflow-definition-file-logging) |
