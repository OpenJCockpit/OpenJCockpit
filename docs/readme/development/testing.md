# Testing

| Layer | Tooling | Where | Runs in CI |
| --- | --- | --- | --- |
| Backend unit and integration tests | JUnit, Testcontainers/mocks, JaCoCo 100% line-coverage gate | `services/*` | Yes, blocking (`backend-ci.yml`) |
| Spec workflow structure validation | JUnit (`SpecWorkflowInitializer` / `SpecStructureValidator`) | `services/embabel-agent-service` | Yes, as part of the backend tests |
| Frontend lint, styles, types, unit tests, build | Nx, ESLint, Vitest | `frontend/` | Yes, blocking (`frontend-ci.yml`) |
| Compose configuration | `docker compose config` | `docker-compose.yml`, `.env.example` | Yes, blocking (`infra-ci.yml`) |
| End-to-end tests | Playwright | `frontend/e2e/` | Reduced mode only, **non-blocking** (`e2e-ci.yml`) |
| Live agent ⇄ LiteLLM ⇄ Ollama check | Playwright (`npm run e2e:llm`) | `frontend/e2e/` | No (needs host Ollama) |
| Live A2A roundtrip | `./scripts/a2a-roundtrip.sh` | `scripts/` | No |

Commands for the backend and frontend checks are in
[Local development](local-development.md#verification).

## End-to-end tests (Playwright)

A `frontend/e2e/` npm package holds the repository's one Playwright installation, covering
`frontend/apps/dashboard` (hub and dashboard) against a running local stack. Full prerequisites, every
environment variable, the token-in-artefacts warning, and everything deliberately excluded (no
positive admin-role coverage, no `WorkflowPromptDialog` dialog-semantics scenario, no wrong-password
scenario, no ESLint) are documented in [`frontend/e2e/README.md`](../../../frontend/e2e/README.md). Read it
before running the suite.

Summary: two mutually exclusive modes, selected by `E2E_MODE` (default `full`), because the Vite dev
server and the containerised dashboard both bind host port 4000:

```bash
# reduced mode — Vite dev server, lighter backend set
cd frontend/e2e && npm run stack:reduced && npm run e2e:reduced && npm run stack:reduced:stop

# full mode — the existing docker compose up stack, operator-started
docker compose up -d --build
cd frontend/e2e && npm run e2e:full
```

Ports used: 4000 (dashboard), 8080 (Keycloak), 9080 (`ai-control-service`).

The suite logs in as the dedicated local `e2e` user (see
[Authentication](../getting-started/authentication.md#keycloak-data)).

> **Warning: test artefacts contain live credentials.** Playwright traces, screenshots, videos and
> storage state can contain live bearer tokens and Keycloak session cookies for the seeded e2e identity.
> Do not share or upload them. See
> [`frontend/e2e/README.md`](../../../frontend/e2e/README.md#warning-traces-screenshots-videos-and-storage-state-contain-live-credentials).

### CI status of the E2E suite

This suite is primarily a **local and QA-agent gate**. The `e2e-ci.yml` workflow runs it in reduced mode
on relevant pull requests and pushes to `main`, as a **non-blocking, informational** leg: it is
intentionally not wired into the required `ci-summary` check, a failure never fails a PR, and it does not
upload `playwright-report`/`test-results` artefacts, because they can contain live tokens. Making it a
blocking gate is a stated follow-up. See [Continuous integration](continuous-integration.md).

### The agent ⇄ LiteLLM ⇄ Ollama live check

`npm run e2e:llm` (from `frontend/e2e/`) proves a real agent LLM call transits the LiteLLM gateway to host
Ollama and is attributed to the expected virtual key and model. It needs the full stack and host Ollama
with the model pulled. See [LLM providers](../integrations/llm-providers.md#litellm-gateway).

## Related

- [Continuous integration](continuous-integration.md)
- [Known limitations](../operations/known-limitations.md#testing-and-ci-limitations)
