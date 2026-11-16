# Local development

How to build, run and verify OpenJCockpit from source. For contribution guidelines see
[CONTRIBUTING.md](../../../CONTRIBUTING.md).

## Requirements

- JDK 25
- Maven 3.9+
- Node.js 22 and npm, only for the dashboard, which lives in the separate [OpenJCockpit-frontend](https://github.com/OpenJCockpit/OpenJCockpit-frontend) repository
- Docker with Docker Compose, for running the full stack
- A local Ollama installation with `qwen3.6:27b`, see [LLM providers](../integrations/llm-providers.md)

## Repository layout

```text
services/
  ai-control-service/      Spring Boot: dashboard API, projects, credentials, spec queue, git-workspace jobs
  embabel-agent-service/   Spring Boot: workflows, agent catalogue, tenant bindings, policy, approval gates (management plane)
  embabel-agent-runtime/   Spring Boot + Embabel: agent execution, LLMs, agent tools, inbound A2A (execution plane)
  git-mcp-server/          Spring AI MCP server: Git and GitHub tools
  service-common/          Shared Java code
agents/                    Local agent catalogue (Markdown agent definitions)
infrastructure/            Keycloak realm + theme, LiteLLM, OPA policies, Postgres init, seeds, stubs
shared/contracts/          OpenAPI contract (openapi.yaml)
scripts/                   Stack start/stop/reset helpers, A2A roundtrip
docs-site/                 Static documentation site (GitHub Pages)
docs/readme/               This documentation
```

## Developer profile: start, reset, stop

The local stack is the **developer profile**: Spring profile `local`, seeded local users and public
placeholder secrets, demo projects and the spec-repository stub. It needs no production credentials
(only an `OPENAI_API_KEY` and a host Ollama, see the [Quickstart](../getting-started/quickstart.md)).

```bash
cp .env.example .env              # once; set OPENAI_API_KEY
./scripts/stack-start.sh          # build + start everything (idempotent); same as: docker compose up -d --build
./scripts/stack-stop.sh           # stop; all state is kept
docker compose down               # remove containers; named volumes are kept
docker compose down -v            # DESTRUCTIVE: also deletes every volume (fresh start next time)
```

Opt-in: `AGENTIC_CONTAINER_ENABLED=true docker compose --profile docker-jobs up -d` gives
`ai-control-service` access to the host Docker daemon for job containers (root-equivalent; see
[Security considerations](../governance/security-considerations.md)).

Production uses different files entirely (`deploy/`, Spring profile `prod`); see the
[Production runbook](../operations/production-runbook.md).

## Running parts of the stack

### Frontend (separate repository)

The dashboard and its federated remotes live in [OpenJCockpit-frontend](https://github.com/OpenJCockpit/OpenJCockpit-frontend), checked out next to this
repository (`../OpenJCockpit-frontend`). The two stacks are independent Compose projects that find each
other through ports published on the host: this stack publishes Keycloak on `8080` and `ai-control-service` on
`9080`; the frontend publishes the dashboard on `4000` and the remotes on `4403`-`4406`.

```bash
# this repository
docker compose up -d --build          # backend stack (or only: docker compose up -d postgres keycloak ai-control-service)

# OpenJCockpit-frontend
docker compose up -d --build          # production-like dashboard + remotes in containers, proxying /api to :9080
# or, for development with hot reload (Vite proxies /api to http://localhost:9080):
npm install && npm run start:hub
```

The containerised dashboard and the Vite dev server both bind port `4000` (a registered Keycloak redirect
URI), so run only one of them. The dashboard calls the backend APIs, so start `ai-control-service` (and what it
depends on) for anything beyond the login and landing hub. `./scripts/stack-start.sh --infra-only` starts only
`postgres` and `keycloak` and prints the extra command to run `ai-control-service` locally.

The dashboard is one shell app (`apps/dashboard`) composing feature libraries under `libs/` (`api`, `project`,
`skills-hub`, `spec-queue`, `spec-files`, `workflow`, plus `auth`, `ui`, `landing-hub`). Boundaries between
them are enforced by `@nx/enforce-module-boundaries`. Each library has its own README, for example [`frontend/libs/workflow/README.md`](https://github.com/OpenJCockpit/OpenJCockpit-frontend/blob/main/libs/workflow/README.md)
and [`frontend/libs/ui/README.md`](https://github.com/OpenJCockpit/OpenJCockpit-frontend/blob/main/libs/ui/README.md) (the self-authored animated/framed
UI); see also [`frontend/apps/dashboard/README.md`](https://github.com/OpenJCockpit/OpenJCockpit-frontend/blob/main/apps/dashboard/README.md).

### Running a Java service on the host

A host-run `embabel-agent-runtime` (for example `mvn -pl embabel-agent-runtime -am spring-boot:run` in the
[OpenJCockpit-Agent-Runtime](https://github.com/OpenJCockpit/OpenJCockpit-Agent-Runtime) checkout) uses `http://localhost:11434` for
Ollama by default, and stores workflow definitions in `${java.io.tmpdir}/embabel-workflow-definitions`
rather than the Compose volume. See
[LLM providers](../integrations/llm-providers.md#local-ollama-qwen-model-prerequisite-before-you-start-the-stack) and
[Runbooks](../operations/runbooks.md#local-non-docker-runs-use-a-different-storage-path).

## Verification

Backend verification (multi-module Maven, from the repo root):

```bash
mvn test
mvn verify

# or just this module:
mvn -pl services/embabel-agent-service -am test
mvn -pl services/embabel-agent-service -am verify
```

`mvn verify` includes the JaCoCo line-coverage gate enforced in CI. The contract tests in
`services/embabel-agent-service` (`ops` package) that compare this repository's Compose and LiteLLM wiring with
the agent runtime read the [OpenJCockpit-Agent-Runtime](https://github.com/OpenJCockpit/OpenJCockpit-Agent-Runtime) checkout next to this one (or
`AGENT_RUNTIME_DIR`); without it they are skipped locally, and backend CI always checks it out. The runtime itself is
verified in its own repository.

Frontend verification runs in the [OpenJCockpit-frontend](https://github.com/OpenJCockpit/OpenJCockpit-frontend) repository (see its README).

`shared/contracts/openapi.yaml` is owned by this repository, but the frontend generates its TypeScript types
from it. After changing the contract, run `npx nx run api:generate` in the frontend checkout (it reads
`../OpenJCockpit/shared/contracts/openapi.yaml`) and commit the result there. The frontend's CI fails when its
generated types are stale against this repository's `main`.

Compose configuration check (what the infra CI leg runs):

```bash
docker compose --env-file .env.example config
```

End-to-end tests: see [Testing](testing.md).

## Related

- [Testing](testing.md)
- [Continuous integration](continuous-integration.md)
- [Spec-driven development](../workflows/spec-driven-development.md)

## Running the agent service and the agent runtime

`docker compose up` starts both as separate containers (the runtime is the released image
`ghcr.io/openjcockpit/embabel-agent-runtime:v0.1.8` of the [OpenJCockpit-Agent-Runtime](https://github.com/OpenJCockpit/OpenJCockpit-Agent-Runtime) repository): `embabel-agent-service` on host port 8091 and
`embabel-agent-runtime` on host port 8092. They start independently (neither waits for the other); until
the runtime is reachable, local agents and definition drafts fail with "agent runtime unavailable".

To run one of them on the host against the rest of the stack, point it at the other by URL:

```bash
# runtime on the host (from the OpenJCockpit-Agent-Runtime checkout), agent service in Compose
cd ../OpenJCockpit-Agent-Runtime
EMBABEL_AGENT_SERVICE_BASE_URL=http://localhost:8091 \
AGENT_RUNTIME_TOKEN_URL=http://localhost:8080/realms/openjcockpit/protocol/openid-connect/token \
AGENT_RUNTIME_CLIENT_SECRET=openjcockpit-agent-runtime-local-secret \
KEYCLOAK_JWK_SET_URI=http://localhost:8080/realms/openjcockpit/protocol/openid-connect/certs \
AGENT_DEFINITIONS_LOCAL_ROOT=embabel-agent-runtime/agent-definitions \
mvn -pl embabel-agent-runtime -am spring-boot:run
```

and set `EMBABEL_AGENT_RUNTIME_BASE_URL=http://host.docker.internal:8082` for the agent service container.

