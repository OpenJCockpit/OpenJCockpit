# Playwright E2E harness

This is the repository's single Playwright installation (AC-01). It drives `apps/dashboard` and
`apps/landing` against a running local stack — it never starts, stops, or wipes that stack itself.

**Before you read the rest of this file:** this suite does **not** cover the platform's core value
path — agent pipelines writing code to customer repositories. It never runs `embabel-agent-service`
to completion, never touches a real git remote, and, except for one narrowly-scoped, precondition-gated exception described below in 'The agent ⇄ LiteLLM ⇄ Ollama live check', never calls an LLM. See "What this suite
deliberately does not cover" below. Do not describe this delivery as "we have E2E coverage" without
that qualifier.

## Prerequisites

- Docker + Docker Compose, able to run this repository's `docker-compose.yml`.
- Node **>= 20** (`@playwright/test@1.63.0`'s own `engines` field). This environment has been
  verified with Node 24.18.0 / npm 12.0.1; `CLAUDE.md` states Node 22 as the repository's general
  target, and either works here — `e2e/package.json` deliberately declares no `engines` field
  narrower than `>=20`, so do not add one.
- A `.env` file at the repository root containing a **non-empty** `OPENAI_API_KEY`. This is a real,
  easy-to-misread trap: `ai-control-service`'s `docker-compose.yml` entry interpolates
  `${OPENAI_API_KEY}` with no default, and its Spring AI startup (`spring.ai.openai.api-key`) fails to
  boot with a blank value. **No scenario in this suite ever makes an LLM call and no valid/working key
  is required** — the variable only needs to be _set to some non-empty string_ so the service can
  boot. `.env.example`'s placeholder value is sufficient.
- Ports **3000**, **4000**, **8080**, **9080** free of anything other than the mode you are about to
  run (see "Two modes" below — the two modes cannot run at the same time on these ports).

### Additional prerequisites for the agent ⇄ LiteLLM check

- Full mode (`E2E_MODE=full`).
- `litellm`, `litellm-seed-key`, and `embabel-agent-service` running.
- Host Ollama running, with the `E2E_OLLAMA_MODEL`/`OLLAMA_MODEL` tag already pulled.
- Host ports `4100` (LiteLLM), `8091` (`embabel-agent-service`), and `11434` (host Ollama) reachable.
- `npm run e2e:llm` additionally requires `keycloak`, `ai-control-service`, `dashboard`, and `landing`
  to be running too: Playwright's global setup gates on Keycloak/`ai-control-service` readiness, and
  the `dashboard` Playwright project's `dependencies: ['setup']` performs a real browser login against
  `:4000` even when only this one spec file is targeted.
- The existing non-empty-`OPENAI_API_KEY` prerequisite (above) is unchanged by this addition and is
  still not a real key.

## Two modes

Selected by the `E2E_MODE` environment variable, default `full`. The suite is one config and one set
of specs; the mode changes only which process serves the frontends and which readiness checks apply.

|                                     | `E2E_MODE=reduced`                                                                       | `E2E_MODE=full` (default)                                                             |
| ----------------------------------- | ---------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| Start with                          | `npm run stack:reduced` (from `e2e/`)                                                    | `docker compose up -d --build` (from the repo root — the existing, unchanged command) |
| Dashboard `:4000` / landing `:3000` | Vite dev servers, started and stopped by Playwright itself                               | nginx containers, operator-owned                                                      |
| Stop with                           | `npm run stack:reduced:stop` (from `e2e/`) — uses `docker compose stop`, never `down -v` | `docker compose stop` / leave running, operator's choice                              |

The two modes cannot run concurrently: the Vite dev server and the containerised dashboard both bind
host port 4000. `globalSetup` detects an already-answering origin in reduced mode and fails fast,
naming BR-21 and telling you to `docker compose stop dashboard landing` first.

Full mode is the production-like mode: it drives the same nginx-built container images that
`docker-compose.yml` runs for a real deployment, not a dev server. Prefer it when the question is
"does the build that would actually ship work end to end", not just "does the source work in dev".
Note that `.github/workflows/e2e-ci.yml` currently runs **reduced** mode only (see "Two modes" table
above and "Warning" below) — full mode today is a local/manual verification step, not something CI
exercises.

## Install, run, report

```bash
cd e2e
npm ci
npx playwright install chromium   # resolves the locally pinned CLI, never `@latest`

# reduced mode
npm run stack:reduced
npm run e2e:reduced
npm run stack:reduced:stop

# full mode (operator starts/stops the stack)
docker compose -f ../docker-compose.yml up -d --build
npm run e2e:full
```

Reports, after a run:

- HTML report: `e2e/playwright-report/` (open with `npx playwright show-report`).
- Machine-readable: `e2e/test-results/report.json` and `e2e/test-results/junit.xml` — these are the
  files a QA transcript or the `Playwright scenarios and results` section of a QA report should cite.
- On failure: a trace, a screenshot, and a video are retained under `e2e/test-results/` for that
  scenario (`trace: 'retain-on-failure'`, `screenshot: 'only-on-failure'`, `video: 'retain-on-failure'`
  — stricter than the `retries: 0` default would otherwise allow a trace to exist for).

## Environment variables

Every variable is resolved once, in `e2e/support/env.ts`, with a documented default. The suite runs
green with none of them set, given the documented environment.

| Variable                       | Default                                                                                                                                             | Used for                                                                                 |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `E2E_MODE`                     | `full`                                                                                                                                              | selects `reduced` or `full` orchestration                                                |
| `E2E_DASHBOARD_BASE_URL`       | `http://localhost:4000`                                                                                                                             | dashboard project base URL                                                               |
| `E2E_LANDING_BASE_URL`         | `http://localhost:3000`                                                                                                                             | landing project base URL                                                                 |
| `E2E_KEYCLOAK_URL`             | `http://localhost:8080`                                                                                                                             | setup project, readiness, Direct Access Grant                                            |
| `E2E_KEYCLOAK_REALM`           | `metafactory`                                                                                                                                       | as above                                                                                 |
| `E2E_KEYCLOAK_CLIENT_ID`       | `metafactory`                                                                                                                                       | as above (public client — no secret variable exists anywhere in this harness)            |
| `E2E_API_BASE_URL`             | `http://localhost:9080`                                                                                                                             | direct-to-`ai-control-service` assertions (e.g. the unauthenticated 401/200 pair)        |
| `E2E_USERNAME`                 | `e2e`                                                                                                                                               | the seeded test identity                                                                 |
| `E2E_PASSWORD`                 | the seeded local-only password documented in the repository root `README.md`'s Keycloak-data table — never repeated here                            | as above                                                                                 |
| `E2E_READINESS_TIMEOUT_MS`     | `120000`                                                                                                                                            | readiness polling timeout, in milliseconds                                               |
| `E2E_LITELLM_BASE_URL`         | `http://localhost:4100`                                                                                                                             | LiteLLM gateway base URL for the attribution check                                       |
| `E2E_EMBABEL_BASE_URL`         | `http://localhost:8091`                                                                                                                             | `embabel-agent-service` base URL for the LLM-path preflight and agent-run polling        |
| `E2E_OLLAMA_BASE_URL`          | `http://localhost:11434`                                                                                                                            | host Ollama base URL for the LLM-path preflight                                          |
| `E2E_LITELLM_MASTER_KEY`       | falls back to `LITELLM_MASTER_KEY`, then the local seed default documented in the repository root `README.md`'s LiteLLM table — never repeated here | Authorization header for LiteLLM spend-log reads (never a query parameter, never logged) |
| `E2E_LITELLM_KEY_ALIAS`        | `embabel-agent-service`                                                                                                                             | expected virtual-key alias for attribution matching (not a secret)                       |
| `E2E_OLLAMA_MODEL`             | falls back to `OLLAMA_MODEL`, then `qwen3.6:27b`                                                                                                    | expected model tag for attribution matching                                              |
| `E2E_REQUIRE_LLM_PATH`         | `false`                                                                                                                                             | when `true`, an unprovisioned LLM path is a hard failure, not a skip (strict mode)       |
| `E2E_AGENT_RUN_TIMEOUT_MS`     | `600000`                                                                                                                                            | budget, in milliseconds, for the agent run to reach a terminal state                     |
| `E2E_LLM_PREFLIGHT_TIMEOUT_MS` | `5000`                                                                                                                                              | per-check timeout, in milliseconds, for the LLM-path preflight probes                    |

This harness does not read the repository root `.env` file — the `LITELLM_MASTER_KEY`/`OLLAMA_MODEL` environment-variable fallbacks above only take effect if those variables are exported into the shell that runs Playwright. Docker Compose's own `.env` file is invisible to this Node process, so do not assume these fallbacks automatically track the running stack's values.

### The `localhost`-only rule (BR-22)

`support/env.ts` throws — before any test runs — if any base-URL variable's hostname is anything
other than the literal string `localhost` (not `127.0.0.1`, not an IP, not any other hostname). This
exists because the `metafactory` realm's `redirectUris`/`webOrigins` are keyed on that exact literal;
`http://127.0.0.1:4000` in particular is **not** a registered redirect URI. Without this guard, a
misconfigured base URL surfaces as an opaque Keycloak `Invalid parameter: redirect_uri` error far away
from the actual cause. The guard turns that into a named, actionable failure at suite startup instead. This rule also covers `E2E_LITELLM_BASE_URL`, `E2E_EMBABEL_BASE_URL`, and `E2E_OLLAMA_BASE_URL`. Note the LLM-path preflight's Ollama reachability probe uses `localhost` while the `embabel-agent-service` container itself reaches Ollama via `host.docker.internal` — on this stack's supported local topology they resolve to the same daemon, so this is a documented approximation, not a guarantee, if that mapping is ever overridden.

## Warning: traces, screenshots, videos, and storage state contain live credentials

**Playwright traces record full network activity, including request headers.** A trace, screenshot,
video, or `e2e/.auth/storage-state.json` produced by this suite can and will contain a live bearer
token and/or live Keycloak session cookies (`AUTH_SESSION_ID`, `KEYCLOAK_IDENTITY`,
`KEYCLOAK_SESSION`) for the local `e2e` test identity. Playwright has no built-in redaction for this.

- **Never** attach a raw trace, screenshot, or video to a GitHub issue, a pull request, or a chat
  message.
- **Never** commit anything under `e2e/.auth/` (gitignored — do not force-add it).
- This is a real, unmitigated-by-redaction risk. It is bounded, not eliminated, by three facts: (1)
  `.github/workflows/e2e-ci.yml` does run this suite in CI now (non-blocking, reduced mode), but it
  deliberately does not upload `playwright-report/` or `test-results/` as a workflow artifact — those
  are discarded with the ephemeral runner when the job ends; (2) nothing
  is committed — `e2e/.auth/`, `e2e/test-results/`, `e2e/playwright-report/`, and
  `e2e/blob-report/` are all gitignored; (3) the `e2e` identity is local-only and worthless off this
  machine — its token's issuer and audience are both `localhost`.
- If this CI leg is ever made blocking or its artifacts are ever uploaded (both stated follow-ups,
  not yet in scope), artefact retention and visibility must be resolved, and this token-exposure risk
  must be re-reviewed first.

The LiteLLM master key deserves a specific callout here: it is used only via raw `fetch` calls in
`support/litellm.ts`, never via Playwright's `request` fixture, and therefore never enters a
Playwright trace, report, screenshot, or video. This is a design constraint, not an implementation
detail — these calls must never be converted to use the `request` fixture.

## The agent ⇄ LiteLLM ⇄ Ollama live check (`specs/agent-llm-gateway.spec.ts`)

This suite contains one narrowly-scoped, precondition-gated exception to "never calls an LLM": a
live scenario that proves a real agent LLM call for the `requirement` agent transits the LiteLLM
gateway to host Ollama, and that the resulting spend-log entry is positively attributed to this
stack's virtual key and configured model.

**Command:** `npm run e2e:llm` (from `e2e/`). It also runs as part of `npm run e2e:full`, since it
is an ordinary spec file discovered by the normal `testMatch` pattern.

**Preflight.** Before attempting anything, the spec runs five ordered checks: full mode is active;
the LiteLLM gateway responds to a liveness probe; `embabel-agent-service` reports `UP`; the host
Ollama daemon is reachable; and the target model tag has been pulled. Each check has its own skip
reason and remediation command. When any precondition is missing, both tests in this spec **skip**
with a named, actionable reason — unless strict mode is set.

**Strict mode.** Setting `E2E_REQUIRE_LLM_PATH=true` (already set by `npm run e2e:llm`) turns a
would-be skip into a hard failure instead. QA is obligated to run this check in strict mode — a
reduced-mode skip must never be cited as evidence that the LLM path works.

**Run budget.** The long-path test allows up to `E2E_AGENT_RUN_TIMEOUT_MS` (default `600000`, i.e.
600 seconds) for the agent run to reach a terminal state. Change it via that environment variable if
needed.

**Stale-image guard (AC-14).** `docker compose build embabel-agent-service` is a prerequisite before
citing a green result from this check — a stale image (built before the current source) will
silently exercise old code, which is the exact "stale-image trap" that originally caused the
confusing "agents don't communicate with the LLM" report this whole delivery investigates (see
`docs/delivery/agent-litellm-communication-fix/00-diagnosis.md` for the full story). A scripted,
automatic "always rebuild before `up`" guard is a named, deliberate follow-up, not built as part of
this delivery.

**No unit-test coverage for `e2e/support/**`.** The four new/extended support modules
(`litellm.ts`, `llmPathPreflight.ts`, `agentRun.ts`, and the `keycloak.ts` additions) have no unit
tests — no unit-test runner exists in `e2e/` today, so this is consistent with the harness's
existing testing posture, not a new gap introduced by this delivery.

**What this does NOT prove:** no MCP tool-calling path is exercised; no git write path is exercised;
no multi-stage orchestration is exercised; no approval gate is exercised; no claim is made about
model output quality.

**`agent_runs` accumulation.** Repeated runs of this spec accumulate rows in the backend
`agent_runs` table. This is expected, benign, and intentionally has no cleanup tooling — building
one would itself be a form of environment management, which this harness's charter forbids.

**"Exactly one new run" is proven structurally**, not by counting database rows: one `POST`, one
returned `runId`. No list-runs endpoint exists for this harness to query, and no row-count assertion
should ever be fabricated here.

**LiteLLM image pin watch item.** This check is verified against the specific pinned
`ghcr.io/berriai/litellm` version this repository's `docker-compose.yml` uses. A future version bump
must re-verify the `/spend/logs/v2` endpoint's query parameters and response envelope shape before
this check can be trusted again.

**`dependencies: ['setup']` note.** This spec's tests run under the `dashboard` Playwright project,
which depends on the `setup` project's real browser login — so this spec inherits that prerequisite
even though it makes no browser/page interaction of its own.

**Residual non-determinism assumption.** No other agent activity should run on this machine while
this check executes, since the "no new attributable gateway row" assertion in the unauthenticated
test would otherwise be unsound. This is safe under this suite's single-worker (`workers: 1`)
execution model.

## What is deliberately excluded, and why

### No role/permission (403) coverage (AC-14)

`infrastructure/keycloak/metafactory-realm.json` defines **no realm roles and no client roles** — it
has no `roles` key populated with role definitions, and no user carries `realmRoles`/`clientRoles`.
Both backend services authorize with `anyRequest().authenticated()` only, with no role or scope check
(`services/ai-control-service/src/main/java/nl/metafactory/aicontrol/config/SecurityConfig.java` and
the equivalent `SecurityConfig.java` in `services/embabel-agent-service`). There is therefore nothing
to construct a 403/role-boundary scenario against today. This suite contains **no** fabricated role
scenario. This is recorded here as a follow-up to be delivered together with the platform's first
Keycloak role, not silently omitted from the QA traceability matrix.

### `WorkflowPromptDialog` dialog semantics — not executable as an E2E scenario today (AC-23)

The architecture for this delivery originally planned an E2E scenario opening `WorkflowPromptDialog`
to prove `role="dialog"`/`aria-modal="true"` semantics, a focus trap, Escape-to-close, and
focus-restore. A live reachability spike found this **is not reachable through the UI today**:

- The seeded `Noordzee Logistics` project's git URL
  (`services/ai-control-service/src/main/resources/db/migration/V2__seed_demo_projects.sql`) does not
  point to a real, clonable repository.
- Both `GET /api/projects/{id}/spec-files` and `GET /api/projects/{id}/spec-init/status` therefore
  return `502 GIT_CLONE_FAILED` against the live stack — verified directly, not assumed.
- `apps/dashboard/src/App.tsx`'s render gates around the `hasSpecs` check (workflow-launcher path,
  ~line 692) and the `specInitStatus?.templateExists` check (the originally-proposed fallback path,
  ~line 743) both then evaluate false, so **neither path that opens `WorkflowPromptDialog` ever
  renders** for the seeded project. The only reachable control in that state triggers a real,
  out-of-scope git push operation, which this suite must not do (see "What this suite deliberately
  does not cover" below).

No `dashboard-dialog.spec.ts` file exists in `e2e/specs/`. AC-23 is instead proven by a Vitest
component test that already exists at
`apps/dashboard/src/workflow/WorkflowPromptDialog/WorkflowPromptDialog.test.tsx`, which covers the
dialog's ARIA role and `aria-modal` attribute, focus moving into the dialog on mount, Escape closing
it, and focus being restored to the previously-focused element — all in jsdom. This mirrors the
treatment AC-14 already receives: named explicitly, with the two files responsible
(`V2__seed_demo_projects.sql` and `App.tsx`), not silently dropped from the traceability matrix.

### No wrong-password scenario

The `metafactory` realm has `bruteForceProtected: true`. A flaky or misconfigured suite that
deliberately submits a wrong password would risk temporarily locking out the shared `e2e` identity,
turning a test-design choice into a self-inflicted outage of the whole suite. No scenario in this
suite submits incorrect credentials.

**Recovery, if the `e2e` identity does get locked out** (e.g. by a developer experimenting locally):
unlock it via the Keycloak Admin Console → realm `metafactory` → Users → `e2e` → the unlock action on
that user's page, or simply wait out Keycloak's configured lockout window.

### No ESLint in `e2e/` (named, accepted gap)

`e2e/` has Prettier (`format` / `format:check`, same pinned version and config as
`apps/dashboard`) but no ESLint. This is deliberate: `apps/dashboard`'s flat ESLint config pulls in
six React-specific packages (`typescript-eslint`, `eslint-plugin-jsx-a11y`,
`eslint-plugin-react-hooks`, `@eslint-community/eslint-plugin-eslint-comments`,
`eslint-config-prettier`, `globals`) that are not justified by the dependency gate for a small,
non-React Node test harness. This is a named, accepted gap — not an oversight — with a stated
trigger for revisiting it: adopt a minimal `typescript-eslint` config for `e2e/` once the harness
exceeds roughly twenty files, or once CI enforcement lands for this suite, whichever comes first.
This delivery adds four new files to `e2e/support/` and `e2e/specs/`, meaning the roughly-twenty-files
trigger above is already crossed as of this delivery; this is recorded here explicitly, not silently
ignored, and adopting ESLint remains out of scope for this delivery because it would require six new
dependencies needing separate sign-off.

### What this suite deliberately does not cover

- **Full agent/LLM pipeline execution — mostly.** One LLM-only, single-stage agent run is now
  covered, under the stated preconditions, by `specs/agent-llm-gateway.spec.ts` (see "The agent ⇄
  LiteLLM ⇄ Ollama live check" above). Everything else in this bullet's original claim still holds:
  no scenario starts a multi-stage workflow run to completion, and no scenario's assertions depend on
  model output quality.
- **Anything requiring a real git remote or credentials.** The seeded project's git URL is not a real,
  writable repository, and this suite never introduces one.
- **Role/permission (403) scenarios** — see above.
- **Visual regression / screenshot diffing.**
- **Automated accessibility scanning** (e.g. axe or equivalent) as a gate. Targeted keyboard/ARIA
  assertions live inside individual scenarios; an automated scanner is a separate tool and a separate
  requirement.
- **The landing intro overlay's own reduced-motion gap (F3, pre-existing, not introduced by this
  delivery).** `apps/landing/src/screens/IntroGreeting/IntroGreeting.jsx` dismisses its full-screen
  overlay with a plain `window.setTimeout(onDone, 2850)` that does **not** check
  `prefers-reduced-motion`, so the ~2.85s overlay covers the hero even under Playwright's
  `reducedMotion: 'reduce'` emulation. This suite's landing specs wait for the overlay to appear and
  then be removed from the DOM as an explicit condition before interacting with anything beneath it —
  they deliberately do **not** assert anything about the overlay's own timing, and AC-24 in those specs
  proves only that the _rest_ of the flow, past the overlay, respects reduced motion. This gap is
  recorded here, not fixed, since fixing it would be a user-visible behaviour change with no
  test-driven necessity.
- **"Visible focus indicator" (AC-21) is verified as real DOM focus, not rendered focus-ring pixels.**
  `dashboard-a11y.spec.ts` and `landing-portal.spec.ts` drive focus with real keyboard `Tab` presses and
  assert the correct element has DOM focus (`toBeFocused()`) at each stop. Neither inspects whether a
  focus ring is actually painted on screen — that would need a visual/screenshot-based check, which is
  out of scope (see "Visual regression" above).

Put plainly: **this suite does not cover the platform's core value path** — agent pipelines writing
code to customer repositories. Whatever this suite proves green, it is not proof that the platform's
reason for existing works end to end.

## The one manual verification step (AC-10)

One assertion in this suite (that the dashboard's project-selection grid is populated from
`ai-control-service`, not a mock fallback) is additionally proven by a one-time manual step, because
automating a mid-suite container stop would itself be a form of environment management this harness
deliberately avoids (it only observes the environment, never manages it):

```bash
docker compose stop ai-control-service
# from e2e/: run dashboard-workspace.spec.ts and observe it fail
docker compose start ai-control-service
```

Both commands are non-destructive and reversible by the second command; neither uses `-v` or `down`.
This step is recorded in the QA evidence when it is performed; it is not part of the routine
`npm run e2e:reduced` / `npm run e2e:full` runs.
