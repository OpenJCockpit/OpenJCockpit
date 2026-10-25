# Security considerations

OpenJCockpit's repository ships a **local development stack**. It is designed to support governed AI
software engineering, but the stack as shipped is **not production-hardened**, has not been
independently security-assessed or certified, and must not be deployed to a shared, internet-facing
or production environment without your own security assessment and hardening.

To report a vulnerability, follow the [security policy](../../../SECURITY.md) (private GitHub
vulnerability reporting, not public issues).

## Local-only credentials and secrets

Every credential below is a **publicly known, local-testing-only** seed value. Never reuse any of them
in CI, staging or production. Replace all of them in any shared environment.

| What | Where | Notes |
| --- | --- | --- |
| Keycloak `master` admin `admin` / `admin` | `docker-compose.yml` | Bootstrap admin. |
| Realm users `tony`, `koen`, `ricky` (`Welkom01!`), `e2e` (`E2eRunner01!`) | `infrastructure/keycloak/openjcockpit-realm.json`, seed scripts | Passwords are intentionally plaintext in the realm import. See [Authentication](../getting-started/authentication.md). |
| LiteLLM master, salt, virtual, A2A and ai-control keys; admin UI `ricky` / `Welkom01!` | `.env.example`, `docker-compose.yml` | See [LLM providers](../integrations/llm-providers.md#litellm-gateway). |
| Keycloak service-client secrets (`openjcockpit-a2a`, `openjcockpit-ai-control`, `openjcockpit-git-mcp`) | `.env.example`, `docker-compose.yml` | |
| `SPEC_QUEUE_RUNNER_TOKEN_SECRET` | `docker-compose.yml` | Public placeholder; set your own (at least 32 bytes) outside local use. |
| `ENCRYPTION_SECRET` / `ENCRYPTION_SALT` (`ai-control-service`) | `docker-compose.yml` | Encrypts stored Git credentials; the defaults are placeholders. |
| `A2A_SERVICE_ENCRYPTION_SECRET` / `_SALT` | `.env.example`, `docker-compose.yml` | Encrypts secrets of A2A services added in the dashboard. Changing them makes stored secrets unreadable. |
| Database users and passwords (PostgreSQL `keycloak`/`keycloak`, `openjcockpit_app`, `embabel_app`, `litellm_app`) | `docker-compose.yml`, `infrastructure/postgres/` | |
| Mock marketplace API key `dev-mock-marketplace-key` | `infrastructure/mock-skills-marketplace/nginx.conf` | Opt-in overlay only. |

Rotating `LITELLM_SALT_KEY` invalidates every credential already encrypted in LiteLLM's database; see
[`infrastructure/litellm/README.md`](../../../infrastructure/litellm/README.md#salt-key-rotation-hazard-r-8).

## High-impact local-development postures

- **Docker daemon access (`docker-socat`).** The stack exposes the host's Docker daemon without TLS on
  the internal Compose network (port `2375`, not published to the host), so `ai-control-service` can
  start workflow containers. Every container in that network effectively has full control over the
  host. Local development only; consider Docker-in-Docker or Kubernetes jobs in production.
- **`git-mcp-server` has no authentication of its own.** It is not published on the host and is only
  reachable through LiteLLM with keys that are explicitly granted the server. Anything that can reach
  it on the Compose network can use it.
- **LiteLLM admin UI** (port `4100`) authenticates against LiteLLM's own credential store, not Keycloak:
  a second authentication surface.
- **Ollama on Linux.** Making the host Ollama reachable from containers on Linux can expose an
  unauthenticated LLM endpoint to your local network. See
  [LLM providers](../integrations/llm-providers.md#local-ollama-qwen-model-prerequisite-before-you-start-the-stack).
- **OPA is published on port `8181`** in the local stack, and its default policy is permissive. See
  [Policy enforcement](policy-enforcement.md).
- **Error messages are disclosed service-wide.** `embabel-agent-service` and `ai-control-service` include
  exception messages in HTTP error responses to authenticated callers. See
  [Approval gates](approval-gates.md#deliberate-behaviour-change-3-embabel-agent-service-includes-exception-messages-in-its-http-error-responses-service-wide).
- **Debug logging can include prompts.** `logging.level.com.embabel.agent: DEBUG` can log prompt
  content; use `INFO` in production.
- **Private egress relaxation.** `SKILLS_MARKETPLACE_ALLOW_PRIVATE_EGRESS` must never be set in any
  deployed environment. See [Skills Hub](../integrations/skills-marketplace.md#warning-skills_marketplace_allow_private_egress-must-never-be-set-in-any-deployed-environment).
- **E2E artefacts contain live credentials.** Playwright traces, screenshots, videos and storage state
  can contain bearer tokens and Keycloak session cookies. See
  [`frontend/e2e/README.md`](../../../frontend/e2e/README.md#warning-traces-screenshots-videos-and-storage-state-contain-live-credentials).

## Authorization model

- All users authenticate through Keycloak; the services validate JWTs.
- **There is no fine-grained authorization** beyond the `openjcockpit-admin` role (spec queue
  auto-merge setting). Any authenticated user can start workflows and decide approval gates, which can
  write to customer repositories through agent runs.
- Service-to-service trust uses dedicated Keycloak clients and narrowly scoped endpoints (for example,
  only `openjcockpit-ai-control` may issue git-workspace tickets, and only `openjcockpit-git-mcp` may fetch
  workspace credentials).
- **Agent service ↔ agent runtime.** Only `openjcockpit-agent-service` may run agents or request drafts on
  `embabel-agent-runtime` (`/internal/v1/**`), and only `openjcockpit-agent-runtime` may read tenant bindings,
  run scopes and tool-call decisions from `embabel-agent-service` (`/internal/agent-runtime/v1/**`). Both
  check the token's `azp` and fail closed; the runtime denies a tool call when no policy decision can be
  obtained. LLM credentials live only in the runtime. See [Agent runtime](../concepts/agent-runtime.md#security-model).
- Multi-tenancy is not production-ready: the tenant claim exists only for the local service client
  (`tenant_id=local`).

## Data sent to LLMs

- Agents send spec content, repository content they read and previous agents' outputs to the configured
  model. In the default setup, the main agent calls go to the **local Ollama** model through LiteLLM,
  while the `best`/`cheapest` aliases and embeddings go to **OpenAI**.
- LiteLLM is configured not to log message content (`turn_off_message_logging`,
  `redact_messages_in_exceptions`).
- The `pii-protection` guardrail checks LLM input and output for configured PII patterns; it is not a
  data-loss-prevention guarantee.
- Git credentials never pass through LiteLLM or an LLM.

## Destructive operations

`docker compose down -v` deletes every named volume, including databases, the Keycloak realm, LiteLLM
state, workflow definitions, both audit trails and Git workspaces. The repository's operating rules
require explicit permission before such a destructive volume action. See
[Runbooks](../operations/runbooks.md#docker-compose-down--v-remains-destructive).

## Before any shared or production deployment

At a minimum:

1. Replace every seeded credential, secret, salt and key, and remove seeded users.
2. Remove the `docker-socat` Docker daemon proxy and choose an isolated job execution model.
3. Put authentication in front of, or remove network reachability to, `git-mcp-server`, OPA and the
   LiteLLM admin UI. Serve `embabel-agent-runtime` and `embabel-agent-service` over HTTPS when they run on
   different hosts, and give their Keycloak clients real secrets.
4. Replace the default OPA policy with your organization's rules and decide on role-based authorization
   for workflow starts and approvals.
5. Review LLM data flows against your data-protection requirements.
6. Address the items in [Known limitations](../operations/known-limitations.md).

This list is a starting point, not a complete hardening guide.
