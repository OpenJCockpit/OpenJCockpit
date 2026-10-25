# Local development

How to build, run and verify OpenJCockpit from source. For contribution guidelines see
[CONTRIBUTING.md](../../../CONTRIBUTING.md).

## Requirements

- JDK 25
- Maven 3.9+
- Node.js 22 and npm (see `frontend/.nvmrc`)
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
frontend/                  Nx workspace: apps/dashboard + libs/* + e2e
agents/                    Local agent catalogue (Markdown agent definitions)
infrastructure/            Keycloak realm + theme, LiteLLM, OPA policies, Postgres init, seeds, stubs
shared/contracts/          OpenAPI contract (openapi.yaml)
scripts/                   Stack start/stop/reset helpers, A2A roundtrip
docs-site/                 Static documentation site (GitHub Pages)
docs/readme/               This documentation
```

## Running parts of the stack

### Local frontend development

You can run Keycloak and PostgreSQL via Docker, and the frontend locally with Vite:

```bash
docker compose up postgres keycloak
cd frontend
npm install                  # installs the Nx workspace
npx nx dev dashboard
```

Then open the Vite URL, http://localhost:4000 (configured in `frontend/apps/dashboard/vite.config.ts`;
it is a registered Keycloak redirect URI). The containerised `dashboard` also binds port `4000`, so do
not run both at the same time. The dashboard calls the backend APIs, so start `ai-control-service` (and
what it depends on) as well for anything beyond the login and landing hub.
`./scripts/stack-start.sh --infra-only` starts only `postgres` and `keycloak` and prints the extra command
to run `ai-control-service` locally plus the Vite dev-server instructions.

The dashboard is one shell app (`frontend/apps/dashboard`) composing feature libraries under
`frontend/libs/` (`api`, `project`, `skills-hub`, `spec-queue`, `spec-files`, `workflow`, plus `auth`, `ui`,
`landing-hub`). Boundaries between them are enforced by `@nx/enforce-module-boundaries`. Each library
has its own README, for example [`frontend/libs/workflow/README.md`](../../../frontend/libs/workflow/README.md)
and [`frontend/libs/ui/README.md`](../../../frontend/libs/ui/README.md) (the self-authored animated/framed
UI); see also [`frontend/apps/dashboard/README.md`](../../../frontend/apps/dashboard/README.md).

### Running a Java service on the host

A host-run `embabel-agent-runtime` (for example `mvn -pl services/embabel-agent-runtime spring-boot:run`) uses `http://localhost:11434` for
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
mvn -pl services/embabel-agent-runtime -am verify
```

`mvn verify` includes the JaCoCo line-coverage gate enforced in CI.

Frontend verification (Nx workspace in `frontend/`: `apps/dashboard`, `libs/*` and `e2e`, one
`package-lock.json`, Node 22 per `frontend/.nvmrc`):

```bash
cd frontend
npm ci
npx nx run-many -t lint check-styles typecheck test build
npx nx affected -t lint test build   # only what changed vs. main
```

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

`docker compose up` starts both as separate containers: `embabel-agent-service` on host port 8091 and
`embabel-agent-runtime` on host port 8092. They start independently (neither waits for the other); until
the runtime is reachable, local agents and definition drafts fail with "agent runtime unavailable".

To run one of them on the host against the rest of the stack, point it at the other by URL:

```bash
# runtime on the host, agent service in Compose
EMBABEL_AGENT_SERVICE_BASE_URL=http://localhost:8091 \
AGENT_RUNTIME_TOKEN_URL=http://localhost:8080/realms/openjcockpit/protocol/openid-connect/token \
AGENT_RUNTIME_CLIENT_SECRET=openjcockpit-agent-runtime-local-secret \
KEYCLOAK_JWK_SET_URI=http://localhost:8080/realms/openjcockpit/protocol/openid-connect/certs \
AGENT_DEFINITIONS_LOCAL_ROOT=services/embabel-agent-runtime/agent-definitions \
mvn -pl services/embabel-agent-runtime spring-boot:run
```

and set `EMBABEL_AGENT_RUNTIME_BASE_URL=http://host.docker.internal:8082` for the agent service container.

