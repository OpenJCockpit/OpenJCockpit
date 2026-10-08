# OpenJCockpit + Keycloak + Custom Login Theme

![image](OpenJCockpit_logo.png)

Futuristic dashboard, build with React, Vite, Docker Compose, Keycloak and PostgreSQL.

## What is in this project?

- React/Vite frontend with a self-authored animated/framed UI (see `apps/landing/src/ui/README.md`).
- Keycloak-authentication with `keycloak-js`.
- The landing page only shown with a valid auth token.
- Intro-animation with `Hi <surname>!`, where the surname is retrieved from the Keycloak-token.
- Topbar with Profile, Notifications, Messages and Logout.
- Two glow-spheres: Business and Implementation.
- Keycloak realm-import file: `keycloak/openjcockpit-realm.json`.
- PostgreSQL as database for Keycloak.
- Custom Keycloak login theme: `themes/openjcockpit/login`.
- The realm `openjcockpit` uses `loginTheme: openjcockpit` by default.

## Starting the application with Docker Compose

### Local Ollama Qwen model (prerequisite, before you start the stack)

The agent pipeline's default LLM (`embabel-agent-service`) runs on a locally
hosted Qwen model served by **Ollama on your host machine**. Ollama is
**not** part of the Docker Compose stack — you install and run it yourself,
before `docker compose up`:

1. [Install Ollama](https://ollama.com) on the host machine.
2. Pull the model tag: `ollama pull qwen3.6:27b`.
3. Keep the Ollama daemon running for the whole time the stack is up — the
   pipeline calls it on every agent run.

**Networking defaults — host-run vs. Compose-run.** The default Ollama base
URL depends on how `embabel-agent-service` itself is running:

| Mode                                             | Default `OLLAMA_BASE_URL`           |
| ------------------------------------------------ | ----------------------------------- |
| Host-run (e.g. `mvn spring-boot:run`, no Docker) | `http://localhost:11434`            |
| Compose-run (inside `docker compose up`)         | `http://host.docker.internal:11434` |

Two environment variables control this, both overridable in `.env`:

| Variable          | Default                                                                             | Meaning                                                   |
| ----------------- | ----------------------------------------------------------------------------------- | --------------------------------------------------------- |
| `OLLAMA_BASE_URL` | `http://localhost:11434` (host-run) / `http://host.docker.internal:11434` (Compose) | Base URL of the Ollama daemon.                            |
| `OLLAMA_MODEL`    | `qwen3.6:27b`                                                                       | The Ollama model tag to use; must exist in `ollama list`. |

**Caveat:** `OLLAMA_BASE_URL` in `.env` applies to the **container**, not your
host. Setting `OLLAMA_BASE_URL=http://localhost:11434` in `.env` breaks the
containerized `embabel-agent-service`, because nothing listens on the
container's own loopback interface. Only override it if your Ollama daemon
listens somewhere other than the Compose default.

**Warning — Linux hosts.** The Compose container reaches Ollama via
`host.docker.internal`. On Docker Desktop (macOS/Windows) this resolves to
the host's default `127.0.0.1`-bound Ollama automatically. On **Linux**,
`host.docker.internal` arrives on a non-loopback host interface, so Ollama
must be bound wider than `127.0.0.1`, e.g. `OLLAMA_HOST=0.0.0.0`. Doing this
**exposes an unauthenticated LLM endpoint to your local network** — anyone
on the same network could submit prompts and consume your GPU. Prefer a
narrower bind (the `docker0`/bridge interface address) or firewall port
`11434` instead of binding to all interfaces.

**Health is not readiness for inference.** `/actuator/health` reports `UP`
even when Ollama is unreachable — no model pull or availability probe is
attempted at startup, by design. The first sign of a problem will be an LLM
call failing at run time, not a failing health check.

### LiteLLM gateway

The agent pipeline's default/coding LLM now routes through a **LiteLLM
gateway container** at host URL `http://localhost:4100`. The container
internally listens on port `4000`, but the host mapping is `4100` because
port `4000` is already used by the `dashboard` service. The host Ollama
prerequisite is **unchanged** — Ollama is still not a Compose service, still
installed and run on the host, still `ollama pull qwen3.6:27b`.

The following environment variables control the LiteLLM gateway. All are
defined in `.env.example` (commented out by default) and overridable in
`.env`:

| Variable               | Default                              | Meaning                                                                 |
| ---------------------- | ------------------------------------ | ----------------------------------------------------------------------- |
| `LITELLM_MASTER_KEY`   | `sk-openjcockpit-litellm-local`       | The LiteLLM proxy admin/master API key.                                 |
| `LITELLM_SALT_KEY`     | `sk-openjcockpit-litellm-salt-local`  | LiteLLM's encryption salt key — changing it invalidates stored credentials. |
| `LITELLM_VIRTUAL_KEY`  | `sk-openjcockpit-embabel-local`       | The virtual key `embabel-agent-service` presents to the gateway.        |
| `LITELLM_UI_USERNAME`  | `ricky`                              | LiteLLM admin UI username.                                              |
| `LITELLM_UI_PASSWORD`  | `Welkom01!`                          | LiteLLM admin UI password.                                              |
| `LLM_CODING_ROUTE`     | unset (commented out)                | Set to `direct` to bypass the gateway and call the host Ollama daemon directly (Revert B, see below). |

**Verifying the gateway path end to end.** An automated, live Playwright
check proves a real agent LLM call actually transits this gateway to host
Ollama and is attributed to the expected virtual key and model: run
`npm run e2e:llm` from `e2e/`. It requires the full stack running (LiteLLM,
`embabel-agent-service`, host Ollama with the configured model pulled, plus
Keycloak/`ai-control-service`/`dashboard`/`landing`) — see `e2e/README.md`'s
"The agent ⇄ LiteLLM ⇄ Ollama live check" section for the exact
preconditions, strict-mode flag, and what it does and does not prove.

**Warning — local-only credentials.** `ricky`/`Welkom01!` and every LiteLLM
gateway key listed above are **local-testing-only seed credentials**. They
must never be reused in CI, staging, or production.

**Non-Keycloak auth gap.** The LiteLLM admin UI on port `4100` is a second
authentication surface backed by LiteLLM's own internal credential store,
not Keycloak. This is an accepted, documented local-development-only gap,
the same posture already used elsewhere in this repo for `docker-socat`
and `git-mcp-server`. Vendor admin-UI accessibility is explicitly out of
scope.

**Worst-case call durations.** One default-LLM call has a worst-case
duration of up to **600 seconds**; one full agent action including its
data-binding re-ask has a worst-case duration of up to **1200 seconds** —
identical to the pre-gateway direct-Ollama path, because the gateway adds
no additional retry multiplication. If the gateway container itself is
down, a call fails in under **45 seconds** per attempt (about **90 seconds**
including the re-ask). A wrong or missing virtual key fails closed in well
under **1 second** total, but as **two** gateway requests, not one — bounded
by `embabel.agent.platform.llm-operations.data-binding.max-attempts=2` (not
an unbounded retry loop). See "Known limitations and follow-ups" below for
why this is two requests instead of one.

**Revert A — to OpenAI:** There is no automatic fallback between providers.
To move the default LLM back to `gpt-4.1-mini`, use either form:

- Environment variable (no rebuild): `EMBABEL_MODELS_DEFAULT_LLM=gpt-4.1-mini`
- `application.yml` edit: `embabel.models.default-llm: gpt-4.1-mini`

This is unchanged and still works precisely because OpenAI is not proxied
through the gateway. **This revert is not automatic** — it requires a
manual step.

**Revert B — to direct host Ollama:** Add `LLM_CODING_ROUTE=direct` to
`.env`, then run `docker compose up -d embabel-agent-service` — no rebuild,
no code edit, no `docker-compose.yml` edit needed. **This revert is not
automatic** — it requires a manual step. Caveat: the `litellm` service's
`depends_on: ... service_healthy` condition on `embabel-agent-service`
still applies, so the `litellm` container must still start even though it
will not be called when `LLM_CODING_ROUTE=direct`; removing that dependency
would itself be a `docker-compose.yml` edit, which is explicitly out of
scope here.

**Release note.** This changes the default LLM for **every** agent in the
pipeline (requirement analysis, impact, test design, implementation, review,
realisation, evidence, prompt-to-definition), not only code generation. The
`best`/`cheapest` aliases and embeddings **remain OpenAI-hosted** — this is
not an "all-local" mode. `OPENAI_API_KEY` **remains mandatory** to boot the
stack; this change makes the pipeline cheaper to _run_, not cheaper to
_start_.

**Release note (LiteLLM gateway).** The default/coding LLM now has a hard
dependency on the `litellm` gateway container; a developer who previously
ran the stack with direct host Ollama must now also run the `litellm`
gateway container. The `best` and `cheapest` LLM aliases and embeddings
still call OpenAI directly (not proxied), so this is explicitly **not** an
"everything through one gateway" change. `OPENAI_API_KEY` remains mandatory
to boot the stack. Both revert paths (A and B above) are manual, never
automatic. The Micrometer/metrics provider tag for default-LLM calls changes
from the Ollama naming convention to the OpenAI naming convention now that
calls are proxied through LiteLLM's OpenAI-compatible endpoint, so
`/actuator/metrics` series names/tags for that call path shift accordingly.
All LiteLLM gateway credentials listed in the variable table above are
local-testing-only.

```bash
cp .env.example .env
# edit .env and add your OPENAI_API_KEY
docker compose up --build
```

You can open the following URLs in your browser:

- App: http://localhost:3000
- Keycloak admin: http://localhost:8080/admin

The app will send you automatically to the custom OpenJCockpit login page of Keycloak. After a valid login, you will be redirected to the landing page.

## Keycloak data

Admin for the `master` realm:

- Username: `admin`
- Password: `admin`

imported realm:

- Realm: `openjcockpit`
- Client: `openjcockpit`
- Login theme: `openjcockpit`
- Default locale: `nl`

Standard users in the `openjcockpit` realm:

| Username | Password    |
| -------- | ----------- |
| `tony`   | `Welkom01!` |
| `koen`   | `Welkom01!` |
| `ricky`  | `Welkom01!` |
| `e2e` (`e2e@openjcockpit.local`) | `E2eRunner01!` |

The `e2e` user is a dedicated, local-only seed identity used exclusively by the Playwright E2E suite
(see "End-to-end tests (Playwright)" below) — deliberately not shared with `tony`/`koen`/`ricky`, so a
flaky or misconfigured test run cannot lock out a real developer's account under the realm's
brute-force protection.

Want your own personal login instead of using the shared users above? See
[infrastructure/keycloak/local-users/README.md](infrastructure/keycloak/local-users/README.md)
for gitignored, per-developer local users.

## Custom login theme

The custom theme has the following location:

```text
themes/openjcockpit/login/
├── login.ftl
├── theme.properties
├── messages/
│   ├── messages_en.properties
│   └── messages_nl.properties
└── resources/
    ├── css/openjcockpit-login.css
    └── js/openjcockpit-login.js
```

The login page remains functionally the standard Keycloak flow: username/password, remember me, reset-password link and any social identity providers are rendered via Keycloak variables. The styling has been replaced with a futuristic OpenJCockpit look, with glow, scanlines, orb, octagon/corner-frame and neon input states.

The `docker-compose.yml` mount the theme to Keycloak:

```yaml
- ./themes:/opt/keycloak/themes:ro
```

For local theme development, the Keycloak theme caches are disabled in Docker Compose:

```yaml
KC_SPI_THEME_CACHE_THEMES: "false"
KC_SPI_THEME_CACHE_TEMPLATES: "false"
KC_SPI_THEME_STATIC_MAX_AGE: "-1"
```

## Re-importing the realm

Keycloak imports the realm on first start into the PostgreSQL database. If the database volume already exists, the existing realm is used. Want to start completely fresh and re-import the JSON?

```bash
docker compose down -v
docker compose up --build
```

## Local frontend development

You can run Keycloak and PostgreSQL via Docker, and the frontend locally with Vite:

```bash
docker compose up postgres keycloak
npm install
npm run dev
```

Then open the Vite URL, usually http://localhost:5173. This URL is already listed in the Keycloak client redirect URIs.

## End-to-end tests (Playwright)

A top-level `e2e/` npm package holds the repository's one Playwright installation, covering both
`apps/dashboard` and `apps/landing` against a running local stack. Full prerequisites, every
environment variable, the token-in-artefacts warning, and everything deliberately excluded (no
positive admin-role coverage, no `WorkflowPromptDialog` dialog-semantics scenario, no wrong-password
scenario, no ESLint) are documented in [`e2e/README.md`](e2e/README.md) — read it before running the
suite.

Summary: two mutually exclusive modes, selected by `E2E_MODE` (default `full`), because the Vite dev
server and the containerised dashboard both bind host port 4000:

```bash
# reduced mode — Vite dev servers, lighter backend set
cd e2e && npm run stack:reduced && npm run e2e:reduced && npm run stack:reduced:stop

# full mode — the existing docker compose up stack, operator-started
docker compose up -d --build
cd e2e && npm run e2e:full
```

Ports used: 3000 (landing), 4000 (dashboard), 8080 (Keycloak), 9080 (`ai-control-service`). This
suite is a **local and QA-agent gate only** — see "Continuous Integration" below.

## Spec queue

### How to use it

- There is one queue per project. Items run one at a time; a manual workflow start is blocked with
  `409 SPEC_QUEUE_ITEM_ACTIVE` while an item is active.
- Auto-merge is off per item by default. The project setting `autoMergeAllowed` defaults to `false`
  and only users with the `openjcockpit-admin` realm role can change it
  (`PUT /api/projects/{projectId}/spec-queue/settings`).
- Actions on items: enqueue, edit, reorder, cancel, remove, plus pause/resume of the queue.

### Configuration

| Variable | Default | Purpose |
|---|---|---|
| `SPEC_QUEUE_RUNNER_TOKEN_SECRET` | local-only placeholder | HS256 key shared by `ai-control-service` and `embabel-agent-service`. Set your own (>= 32 UTF-8 bytes) outside local use. Blank = runner unconfigured; non-blank under 32 bytes fails startup. |
| `SPEC_QUEUE_RUNNER_ENABLED` | `true` | `false` stops the runner from starting queued items. |
| `SPEC_QUEUE_POLL_INTERVAL` | `PT20S` | Duration between runner ticks (`PT1S` to `PT1H`). |

`SPEC_QUEUE_START_LEASE`, `SPEC_QUEUE_MERGE_LEASE` (`PT5M`), `SPEC_QUEUE_EMBABEL_TIMEOUT` and
`SPEC_QUEUE_GITHUB_TIMEOUT` (`PT30S`, max `PT30S`) are read by the backend `application.yml` only.
GitHub calls that time out are retried twice.

### The `openjcockpit-admin` role

Seeded for `tony` by the realm import and the `keycloak-realm-roles` one-shot (see
`infrastructure/keycloak/local-users/README.md`). It surfaces as `ROLE_openjcockpit-admin` from the
token's `realm_access.roles`. The runner itself is not a Keycloak identity: it uses a 60 s HS256 JWT
signed with the shared secret.

### GitHub token scopes

Pull requests read/write, Contents write, Checks read, Commit statuses read (classic: `repo`), and a
role that is allowed to merge. Merging is GitHub-only.

### Residual risks

- R2, R6, R9: see the "Spec queue" section of `docs-site/index.html`.

### Rollback

Rollout order: contract and migration V12, embabel, ai-control, compose secret and role seed,
dashboard. Switch the feature off with `SPEC_QUEUE_RUNNER_ENABLED=false`. Dropping the four queue
tables is a manual, destructive step; never use `docker compose down -v`.

### Optional manual Postgres check

`sh scripts/spec-queue-postgres-check.sh` (not part of CI).

## Important

The passwords are intentionally plaintext in `keycloak/openjcockpit-realm.json`, because this is a local dev/import setup. Do not use this as-is in production.

## MCP: central tools for agents (git-mcp-server)

The platform tools for agents run through the Model Context Protocol (MCP), so
the implementation lives in one place and every agent (or external MCP client)
uses the same tools.

**Server — `services/git-mcp-server`** (Spring AI MCP server, port 8093): offers
the git integration as tools. Every tool receives the project details for the
git integration (repository URL and credentials) and works in one managed
workspace clone per repository:

| Tool                      | Purpose                                                                                |
| ------------------------- | -------------------------------------------------------------------------------------- |
| `git_checkout_branch`     | Check out an existing branch (clones the repository if needed)                         |
| `git_create_branch`       | Create a new branch from a base branch (with a prior pull)                             |
| `git_pull`                | Fetch the latest changes for the active branch                                         |
| `git_commit`              | Stage and commit all changes (returns the commit hash)                                 |
| `git_write_file`          | Write a text file to a relative path in the workspace clone (e.g. `specs/spec-001.md`) |
| `git_push`                | Push the branch to origin so a pull request can be opened                              |
| `git_create_pull_request` | Open a pull request on GitHub for a pushed branch (returns the PR URL)                 |

The JGit implementation sits behind a `GitWorkspaceOperations` port
(the same pattern as in ai-control-service): if the git implementation
changes, that happens in one place.

**Client — embabel-agent-service**: the `RemoteMcpToolExecutor` connects to one
or more MCP servers (configuration `openjcockpit.mcp.servers`, MCP Java SDK over
SSE) and routes every tool call to the first server that offers the tool.
Tool calling stays centralized: agents only reach tools through the
existing `PolicyGuardedMcpToolGateway`, so every call first passes the
OPA policy check. Enable via `MCP_CLIENT_ENABLED=true` (enabled in Docker
Compose, with `GIT_MCP_SERVER_URL=http://git-mcp-server:8093`); when disabled,
behavior falls back to the no-op executor.

### Per-run workspace isolation (`workspaceKey`)

All seven git tools accept an optional `workspaceKey` parameter. When supplied,
it is validated against `^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$` and rejected before
any filesystem access if it does not match; when accepted, it isolates that
call's working directory from other concurrent runs against the same
repository. When omitted or blank, behaviour is unchanged from before this
parameter existed (the legacy shared-workspace path).

Two environment-overridable tunables govern cleanup of these per-run
workspace directories, following the project's `${VAR:-default}` convention:

| Variable                              | Default | What it bounds                                                                       |
| -------------------------------------- | ------- | ---------------------------------------------------------------------------------------- |
| `GIT_MCP_RUN_WORKSPACE_RETENTION`      | `PT24H` | How long a per-run workspace directory is retained before it is eligible for reaping.    |
| `GIT_MCP_RUN_WORKSPACE_REAP_INTERVAL`  | `PT1H`  | How often the reaper scans for expired per-run workspace directories.                    |

**Rollback remedy for leftover `runs/` directories.** If `git-mcp-server` is
rolled back to a version that predates per-run workspace isolation, any
`runs/` subdirectories left behind under the workspace base path do not harm
the older version (it never looks there) but do consume disk space until the
retention window would otherwise have reaped them. The targeted remedy is a
`rm -rf` of `/git-mcp-workspaces/runs` **inside the running container** (not
the whole volume). Do **not** run `docker compose down -v` as a "quick fix"
for this — that command destroys the *entire* `git-mcp-workspaces` volume,
including the shared legacy workspace clones every run still depends on, and
this repository's operating rules require explicit user permission before any
such destructive volume action.

## Default workflows (automatically imported)

On startup, the embabel-agent-service automatically imports a default
workflow bundle (`spec-workflow/default-workflows.json`) with the group
**Spec-driven development** and four workflows:

| Workflow                      | Id                  | Purpose                                                                                                                                                                                                                                                        |
| ----------------------------- | ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Spec folder initialization    | `wf-spec-init`      | Pushes `.specify/templates/spec-template.md` as a pull request so agentic workflows can run against the repository.                                                                                                                                            |
| Create spec from prompt (RAG) | `wf-spec-create`    | Creates small, executable spec files in `specs/` based on a prompt and the repository content (RAG).                                                                                                                                                           |
| Implement spec                | `wf-spec-implement` | Generates an implementation plan (architecture, changes, test plan, review) for the selected spec file and publishes it as `specs/implementation-<run-id>.md` on a dedicated `impl/` branch with a pull request. Delivers **no code changes** — only the plan. |
| Realize spec (code)           | `wf-spec-realise`   | Actually carries out the implementation: see below.                                                                                                                                                                                                            |

### Realize spec (code) — the agent that does the real work

`wf-spec-realise` is the workflow that writes code, as opposed to
`wf-spec-implement` which only produces a plan. When started (with a
feature spec selected in the dashboard):

1. The pipeline first runs impact analysis, test design, implementation plan,
   and review — the same steps as `wf-spec-implement` — but does not yet
   publish a separate result for that.
2. The **realization phase** (`CodeRealisationService`) clones the repository and
   looks in `specs/` for an existing `implementation-*.md` that references
   the selected feature spec.
   - **Found** → that plan is used; the LLM output from step 1 is
     discarded (no duplicate plan).
   - **Not found** → the plan generated in step 1 is used and committed in
     the same branch as `specs/implementation-<run-id>.md`.
3. The realization sub-agent (`CodeRealisationAgent`) selects relevant
   files from the repository (`git_list_files`/`git_read_file`) and
   generates the full new content of each changed file based on
   the plan and that existing code.
4. All changes (plus the plan, if new) are committed, pushed
   to `feat/<workflow-id>-<run-id>` and opened as a pull request.

This is a deliberate design choice: the pipeline does **not** wait until a
separate `wf-spec-implement` pull request is manually merged before
realization starts — that would block the pipeline on a human. Instead,
the realization phase itself checks whether a plan already exists and
generates one itself if needed, so that a single click on **Start** always
ends in one review-ready pull request with both the plan (if new) and the
code. If you want the plan approved separately first, manually start
`wf-spec-implement`, merge that PR, and then start `wf-spec-realise` — the
realization phase will then find the merged plan in `specs/` and effectively
skip its own LLM planning steps (the result is discarded once
the existing plan is found).

The import is idempotent: records are only added if their id does not yet
exist, so your own changes to these workflows are preserved. Disable
with `openjcockpit.workflow-definitions.seed-defaults=false`.

Without specs, the dashboard shows one of two flows depending on the repository state:

- **No `.specify` folder on the base branch** → the spec-init flow: a starter template
  is offered as a pull request (or the link to an already open spec-init branch).
- **A `.specify` folder exists, but no specs yet in `specs/`** → the pull-request flow is
  skipped and the dashboard shows the **✨ Create spec with prompt** button, which starts the
  _Create spec from prompt (RAG)_ workflow via the prompt dialog. The status endpoint
  (`GET /api/projects/{id}/spec-init/status`) reports this via the `templateExists` field.

The **Create spec from prompt (RAG)** workflow has `promptRequired: true`:
when started, the dashboard asks for a prompt in a dialog (in the same futuristic
house style). The prompt and/or the selected spec file are sent as a
JSON body to `POST /api/workflows/{id}/start`
(`{ "prompt": "...", "specFile": "..." }`); an explicit prompt takes priority over the
selected spec file as the payload for the agent run. The _Ask for
prompt on start_ field in Workflow Design enables this behavior on custom workflows.

Workflows also have a **promptInstructions** field: instructions that are
prepended to the user prompt when starting. For _Create spec
from prompt (RAG)_, these contain the full instructions for the agent: create a
branch (`spec/<NNN-feature-slug>`), create the spec based on
`.specify/templates/spec-template.md`, update `specs/_index.md`, keep the
spec small enough for a single implementation run, and commit and push the branch to
origin so a pull request can be opened. Custom prompt workflows
can fill in their instructions via _Prompt instructions (prefix)_ in
Workflow Design.

### Spec and implementation publishing to git (deterministic)

The git steps do not depend on whether the LLM follows the prompt
instructions: the embabel-agent-service publishes run results to git itself via the
`SpecGitPublisher` (`git_create_branch` → `git_write_file` → `git_commit` →
`git_push` → `git_create_pull_request`, all via the policy-gated
MCP gateway to the git-mcp-server):

- **After the requirement phase** (spec creation): branch `spec/<workflow-id>-<run-id>`
  with the spec as `specs/spec-<run-id>.md`.
- **After the implementation/review phase** (spec implementation): branch
  `impl/<workflow-id>-<run-id>` with the implementation plan, test plan, and
  review findings as `specs/implementation-<run-id>.md`.

Both publications automatically open a pull request to the base branch
(GitHub API, using the token from the project credential); if that fails, the
`git` event contains a compare URL to open the PR manually. The
PR URL appears as a `pull-request:` artifact on the run.
Note: the implementation agent currently generates a _plan_ (no actual
code changes); the plan is offered as a reviewable PR.

The run shows every publication as a `git` event
(OK/SKIPPED/FAILED) and registers the branch as an artifact. Errors from the
git tools (e.g. a failed clone or push) are shown as a FAILED event with the
actual error message — the run does not fail because of it, but it is visible.

The repository URL and git credentials come from the selected project: the
dashboard sends the project id when starting, the ai-control-service fills in
the project's git URL and decrypts the active project credential
(Settings → Git credentials) toward the agent service. Without a
project URL, the run falls back to the workflow's execution configuration;
without a project credential, to `SPEC_GIT_USERNAME`/`SPEC_GIT_TOKEN`. Other
configuration via `SPEC_GIT_ENABLED` and `SPEC_GIT_BASE_BRANCH` (see
`openjcockpit.spec-git` in the embabel-agent-service's application.yml).

## Workflow Design: groups and JSON export/import

Workflows in the Workflow Design & Execution screen can be bundled into **workflow
groups**: logical groups for workflows with a shared meaning (for example
onboarding, compliance, or reporting).

- Manage groups via the **Groups** tab in Workflow Design (create, edit, delete).
- Link a workflow to a group via the **Group** field in the workflow form, and
  filter the workflow table by group via the group filter in the toolbar.
- Deleting a group unlinks the associated workflows; the workflows themselves
  continue to exist.

Project assignment and visibility:

- Both workflows and groups can be linked to a project (field _Project name_).
- A workflow **without a group must** have a project; a workflow **with a group may**
  be project-less. Workflows without a group and without a project are rejected (400), both
  on create/edit and on JSON import.
- Which project a workflow "sees": the workflow's own project wins; if that is empty,
  the group's project applies. If that is also empty, the workflow is **global** and
  available for every project — this is how the three default workflows from the global group
  _Spec-driven development_ can be started for any project, both from the dashboard and
  manually from the Overview tab.

Workflows and groups can be exported and imported together as JSON, for example
to move them between environments:

- **⬇ Export JSON** in the Workflows tab downloads `workflow-export.json` containing
  `{ "groups": [...], "workflows": [...] }`.
- **⬆ Import JSON** reads that same format back in. Records are upserted by `id`;
  records without an `id` automatically get a generated id.

The underlying endpoints (proxied by the ai-control-service to the
embabel-agent-service):

```text
GET    /api/workflow-groups            # list of groups
POST   /api/workflow-groups            # create a group
PUT    /api/workflow-groups/{id}       # update a group
DELETE /api/workflow-groups/{id}       # delete a group (unlinks workflows)
GET    /api/workflows/export           # exports groups + workflows as a JSON bundle
POST   /api/workflows/import           # imports a previously exported bundle
```

## Spec-driven development workflow

All agentic AI feature work follows a spec-driven workflow. The structure, templates, and rules
live in `services/embabel-agent-service/` (see also `services/embabel-agent-service/AGENTS.md`).

Initialize spec workflow:

The structure is automatically created and validated on startup of the
embabel-agent-service (`SpecWorkflowInitializer`, can be disabled with
`SPEC_WORKFLOW_INIT_ENABLED=false`). The same validation runs as a JUnit test on every build:

```bash
mvn -pl services/embabel-agent-service test
```

Backend verification (multi-module Maven, from the repo root):

```bash
mvn test
mvn verify

# or just this module:
mvn -pl services/embabel-agent-service test
mvn -pl services/embabel-agent-service verify
```

Frontend verification (dashboard, with package-lock.json → npm ci):

```bash
cd apps/dashboard
npm ci
npm test
npm run build
```

Spec folder rules:

- Every feature must have its own folder under `specs/`.
- The folder name must match `NNN-feature-slug`.
- Every feature folder must contain `spec.md`, `plan.md`, `tasks.md`, and `review.md`.
- The only Markdown file allowed directly in `specs/` is `_index.md`.
- Flat spec files are forbidden.

Templates for new features are located in `services/embabel-agent-service/templates/specs/`.
Copy them to `specs/NNN-feature-slug/` and update `specs/_index.md`.

## Local Skills Marketplace double (opt-in, dev-only)

The `ai-control-service` can list skills from registered "Skills Marketplace" connections
(Skills Hub settings page) via `GET /api/skill-catalog`, using an invented placeholder wire
contract: `GET {marketplaceUrl}/skills` returning a JSON array of `{name, description}`
objects, authenticated with `Authorization: Bearer <apiKey>`. No real marketplace vendor
exists yet, so this repository ships a tiny fake marketplace (`nginx` serving a static
`skills.json`) purely so a developer can click through the feature by hand. It is
**test/dev-only infrastructure**: it has no build context, is not part of the default
`docker compose up`, is not referenced by any production code path or seed data, and is not
required by any automated test (those use an in-process HTTP double instead).

### Starting it

The mock is defined in a separate, never-auto-merged overlay file
(`docker-compose.local-marketplace.yml`, **not** `docker-compose.override.yml`). It only
starts when you explicitly add it with a second `-f`:

```bash
docker compose -f docker-compose.yml -f docker-compose.local-marketplace.yml up
```

A plain `docker compose up` (no overlay) never starts `mock-skills-marketplace` and never
touches the egress guard described below — the default startup path is unchanged.

### `SKILLS_MARKETPLACE_*` environment variables

These configure the `ai-control-service` outbound call to any registered marketplace
(real or mock). All have safe defaults and none are required for normal operation:

| Variable                                       | Default  | Meaning                                                                                                               |
| ---------------------------------------------- | -------- | --------------------------------------------------------------------------------------------------------------------- |
| `SKILLS_MARKETPLACE_REQUEST_TIMEOUT_SECONDS`   | `5`      | Overall per-marketplace request deadline, in seconds.                                                                 |
| `SKILLS_MARKETPLACE_CONNECT_TIMEOUT_SECONDS`   | `2`      | TCP connect timeout, in seconds.                                                                                      |
| `SKILLS_MARKETPLACE_MAX_RESPONSE_BYTES`        | `262144` | Maximum response body size (bytes) before the read is aborted.                                                        |
| `SKILLS_MARKETPLACE_MAX_ITEMS`                 | `500`    | Maximum number of skill items accepted from one marketplace response.                                                 |
| `SKILLS_MARKETPLACE_MAX_REDIRECTS`             | `1`      | Maximum number of HTTP redirects followed.                                                                            |
| `SKILLS_MARKETPLACE_MAX_CONNECTIONS_PER_FETCH` | `25`     | Maximum number of marketplace connections queried in one fetch cycle.                                                 |
| `SKILLS_MARKETPLACE_ALLOW_PRIVATE_EGRESS`      | `false`  | Deny-by-default egress guard opt-out for loopback/RFC1918/link-local/private destinations. **See the warning below.** |

### Trying it out against the mock

1. Start the stack with the overlay (command above).
2. Open the dashboard, go to the Skills Hub settings page, and register a new marketplace
   connection by hand:
   - Marketplace URL: `http://mock-skills-marketplace/`
   - API key: `dev-mock-marketplace-key` (the fixed, obviously-fake dev credential the mock
     checks for — see `infrastructure/mock-skills-marketplace/nginx.conf`; it is never a
     real secret and must never be reused anywhere else)
3. Enable the connection, then open the Skills tab. The mock's `skills.json` fixture
   (`infrastructure/mock-skills-marketplace/skills.json`) is returned as that connection's
   skill catalogue. An incorrect or missing API key gets a `401` from the mock, which the
   feature reports as an authentication failure.

No connection pointing at the mock is seeded anywhere — you must register it yourself
through the settings UI.

### **Warning: `SKILLS_MARKETPLACE_ALLOW_PRIVATE_EGRESS` must never be set in any deployed environment**

This flag exists solely so the feature can be exercised against a marketplace that resolves
to a Docker-network private address (as the mock does). It relaxes the egress guard's
deny-by-default policy for loopback, RFC1918, link-local, CGNAT, and IPv6 ULA destinations.
It is a **local-development-only credential-guard relaxation**, is off by default in both the
shipped `application.yml` and the base `docker-compose.yml`, and is only ever turned on by
the opt-in overlay above. Never set it in a staging or production environment.

Even with this flag enabled, cloud-metadata addresses (for example `169.254.169.254`) stay
blocked — by design, the flag never widens the guard to cover metadata endpoints, regardless
of flag state.

### Known limitation: double `/skills` if the stored URL already ends in `/skills`

The feature composes the request URL by appending `/skills` to the stored marketplace URL.
If you register a marketplace URL that already ends in `/skills` (e.g.
`http://example.com/skills`), the actual outbound request goes to
`http://example.com/skills/skills`. The Skills Hub settings page's help text does not
currently warn about this. Fixing that help text is out of scope for this change; register
the marketplace's base URL without a trailing `/skills` segment.

## Operations runbook: human-in-the-loop workflow approval gate

This section documents the operational consequences of the workflow approval gate
feature in `embabel-agent-service`: three configuration tunables, two deliberate
behaviour changes, a rollback hazard that requires a manual step, and three
operating characteristics an operator should be aware of before restarting or
redeploying the service.

### The three tunables

The `embabel-agent-service` service in `docker-compose.yml` exposes three
environment-overridable tunables, following the project's existing
`${VAR:-default}` convention:

| Variable                                | Default | What it bounds                                                                                                                                                                                                               |
| --------------------------------------- | ------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `APPROVAL_GATE_MAX_FEEDBACK_ITERATIONS` | `3`     | The maximum number of "accept with comments" feedback loops allowed at a single approval gate placed after the `realisation` stage (i.e. at most 4 gate openings per run: the initial one plus up to 3 feedback iterations). |
| `APPROVAL_GATE_COMMENT_MAX_LENGTH`      | `4000`  | The maximum length, in characters, of the free-text comment an operator can submit with an "accept with comments" decision.                                                                                                  |
| `APPROVAL_GATE_MAX_CHANGED_PATHS`       | `50`    | The maximum number of changed file paths shown in the approval context payload before the remainder are summarized as "N more files".                                                                                        |

### Workflow-trigger tunables (workflow chaining)

The `embabel-agent-service` service also exposes four environment-overridable
tunables for workflow chaining (a workflow orb that triggers another workflow
as a child), following the same `${VAR:-default}` convention:

| Variable                                  | Default | What it bounds                                                                                                                                 |
| ------------------------------------------ | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| `WORKFLOW_TRIGGER_SEQUENTIAL_MAX_WAIT`     | `30m`   | The maximum time a SEQUENTIAL parent waits for its child workflow to finish before the parent times out.                                       |
| `WORKFLOW_TRIGGER_MAX_CHAIN_DEPTH`         | `3`     | The maximum chain depth. A cost and blast-radius control: each additional chain level multiplies LLM calls, git operations, and pull requests. |
| `WORKFLOW_TRIGGER_MAX_ORBS_PER_WORKFLOW`   | `5`     | The maximum number of workflow orbs allowed on a single workflow definition.                                                                    |
| `WORKFLOW_TRIGGER_ENABLED`                 | `true`  | Runtime kill switch for the whole workflow-trigger feature.                                                                                      |

This new timeout is deliberately unrelated to `ExecutionConfig.timeoutSeconds`,
which is dead configuration — persisted and exposed in the API contract but
never read by any code.

**`APPROVAL_GATE_MAX_FEEDBACK_ITERATIONS` is effectively the only cost control this
feature has.** Every time an operator presses "accept with comments" on an approval
gate, the system re-runs the full `realisation` stage: roughly two additional LLM
calls plus a full repository clone/read cycle, followed by an additional commit
pushed to the run's branch. There is no other cost meter, rate limit, or token
budget anywhere in this platform. Left unbounded, an operator could multiply a
single run's LLM and git-push cost simply by repeatedly requesting revisions.
Lowering this value is the only lever available to cap that cost; there is no
alternative safeguard to fall back on.

### Deliberate behaviour change 1: unknown run ids now surface as "run state lost", not an endless "waiting" poll

Before this feature, polling the status of an unknown or expired run id returned
an HTTP 200 with a status value the dashboard did not recognize. The dashboard
does not treat unrecognized statuses as terminal, so it kept polling forever and
showed a "waiting" indicator that never resolved — a pre-existing bug.

This feature fixes that: any unknown run id (for example, after a service
restart, or a stale bookmark, or a mistyped run id) now surfaces as a clear,
terminal "run state lost" outcome in the dashboard. The dashboard stops polling
and offers the operator a way to start the workflow again.

This applies to **every** unknown run id, not only runs that were paused at an
approval gate. It is worth calling out explicitly because it changes visible
dashboard behaviour broadly — including for runs and workflows that never use
the new approval gate feature at all — not just for approval-gate scenarios.

### Deliberate behaviour change 2: "no changes produced" is no longer reported as a failure

Previously, when a code-generation (`realisation`) stage produced no changes —
either because the agent decided nothing needed changing, or because there was
genuinely nothing new to commit — the run was reported as a technical failure.

This feature changes that: a no-op outcome is now reported as its own distinct
"no change" outcome, separate from a technical failure. If the workflow has an
approval gate configured, the gate still opens normally on this outcome so a
human can review and decide (accept, deny, or — where the gate supports it —
request a further iteration) rather than the run looking like it errored.

Runs on workflows **without** an approval gate configured behave exactly as
before this change: a "no changes" outcome from `realisation` does not surprise
an operator with new status values they have not seen previously in that
scenario.

### Deliberate behaviour change 3: `embabel-agent-service` now includes exception messages in its HTTP error responses, service-wide

To let the approval gate's validation error messages actually reach the
frontend, this feature adds `server.error.include-message: always` to
`embabel-agent-service`'s `application.yml`.

This setting is **not scoped to the approval-gate endpoints** — it applies to
**all** endpoints in `embabel-agent-service`. As a result, any unhandled
server-side error anywhere in that service will now surface its exception
message to an authenticated caller, where previously that message would have
been suppressed from the HTTP response body.

This is a deliberate, accepted trade-off for this feature, not a bug. It is a
mild, service-wide widening of information disclosure: operators should be
aware that error responses from `embabel-agent-service` may now be more
verbose and informative than before this feature, across the whole service —
not only for approval-gate scenarios.

**Update (D-QA-1 remediation):** the `server.error.include-message: always`
setting above was in fact a no-op in `embabel-agent-service` from the original
delivery of this feature until this remediation. The Embabel agent platform
(at the time, version 0.2.0) auto-registered its own error-controller bean,
which silently shadowed Spring Boot's default error controller and never read
`server.error.include-message` at all — so, despite being present in
`application.yml`, exception messages were never actually surfaced in HTTP
error responses until this fix landed. The disclosure widening described
above was therefore only real as of this remediation; it was not in effect
during the environment the original QA pass swept. At the time, the fix added
a new `ErrorHandlingConfig` bean in `embabel-agent-service` that replaced the
platform's error-controller bean with Spring Boot's own, unmodified one, which
required `spring.main.allow-bean-definition-overriding: true` in
`application.yml` so that this application's own bean could win over the
platform's same-named bean regardless of component-scan order.

**Update (framework upgrade to Spring Boot 4.1.1 / Spring AI 2.0.1 / Embabel
1.5.1):** the `ErrorHandlingConfig` bean described above has since been
**deleted**, and `spring.main.allow-bean-definition-overriding` has been
**removed** from `embabel-agent-service`'s `application.yml`. Embabel 1.5.1
ships zero MVC controller classes at all — unlike 0.2.0, there is no longer a
competing platform error-controller bean to shadow or override — so Spring
Boot's own default `BasicErrorController` is active, untouched, in this
service. Under Boot 4.1.1, the property that actually controls whether the
`message` field appears in error responses is `spring.web.error.include-message: always`,
not the old `server.error.include-message`, which is now inert for this
purpose; both `embabel-agent-service` and `ai-control-service` use
`spring.web.error.include-message: always` consistently. The **user-visible
behavior is unchanged**: error responses from both services still include a
`message` field, service-wide, exactly as before — only the internal
mechanism changed, and the previous `ErrorHandlingConfig`/
`allow-bean-definition-overriding` workaround is no longer necessary and has
been removed, not reinstated or altered in behavior.

### Rollback hazard: removing a saved `approvalGate:` configuration before rolling back past this feature

Once any workflow has been saved with an approval-gate configuration through
this feature, the workflow definition YAML file gains an `approvalGate:` key.

**If, and only if,** the `embabel-agent-service` deployment is ever rolled back
to a version that **predates this entire feature**, that older code cannot
parse the new `approvalGate:` field. The observed failure mode is severe and
not self-healing:

- The workflow list endpoint fails to load, returning an internal server error.
- The startup seed/definition-import process fails **at boot**, so the older
  service instance may not come up cleanly at all.

**Required manual step before such a rollback:** find every workflow definition
YAML file under the `workflow-definitions` volume that contains an
`approvalGate:` key, and remove that key (or the whole file, if the workflow is
disposable) before starting the older version of `embabel-agent-service`.

**This hazard is narrower than it might sound — do not over-apply this
warning:**

- Rolling back **only the frontend** (`apps/dashboard`) is fine and needs no
  special step.
- Rolling back **only the backend**, to a version that already includes this
  feature's initial groundwork (i.e. a version that can already parse
  `approvalGate:`, even if earlier than the final feature), is fine and needs
  no special step.
- The hazard applies **only** to a rollback of `embabel-agent-service` to a
  version that predates the whole feature, i.e. one whose code has never heard
  of the `approvalGate:` field.

### In-flight paused runs do not survive a restart or redeploy

A run that is currently sitting at an approval gate, waiting for a human
decision, is held in memory only. It does **not** survive a restart or
redeploy of `embabel-agent-service`. After such a restart, polling that run's
id will show the "run state lost" outcome described above, and the workflow
must be started over from scratch — there is no way to resume a paused run
across a restart.

Approval decisions that were **already made** before the restart are not lost:
they are recorded durably (under the same `workflow-definitions` volume the
audit trail already uses) and remain readable afterwards. Only the in-progress,
not-yet-decided run state is lost — never the historical decision record.

**Operational guidance:** before restarting or redeploying
`embabel-agent-service`, check whether any runs are currently sitting at an
approval gate awaiting a decision, and let operators know those runs will need
to be restarted from scratch.

### No new authentication or authorization role is introduced

Approving, denying, or commenting on an approval gate is available to any
authenticated user of the platform. This matches how every other feature in
this platform currently works — there are no fine-grained user roles defined
anywhere in the Keycloak realm today.

This is a deliberate, accepted posture for this delivery, not an oversight.
Introducing proper role-based restriction on who may approve pipeline changes
that write to a customer repository is flagged as valuable future work, and is
explicitly not addressed here. No Keycloak realm, client, or role configuration
was changed as part of this feature.

### Resolved: per-run workspace isolation replaces the one shared git working copy per repository limitation

`git-mcp-server` used to keep a single, shared working copy per repository
URL, so two runs operating on the same repository at the same time could
interfere with each other's checked-out branch. This was flagged as a
pre-existing limitation, made more noticeable by the approval-gate feature's
"accept with comments" retry loop, which kept a run active against a
repository for longer and widened the collision window.

This limitation is now resolved for callers that opt in: the optional
`workspaceKey` parameter (see "Per-run workspace isolation" under
"MCP: central tools for agents (git-mcp-server)" above) isolates each run's
working directory from every other concurrent run against the same
repository. `embabel-agent-service`'s own `GitToolClient` already does this
automatically for every git tool call it makes — it unconditionally sets
`workspaceKey` to the run id — so this platform's own agent runs no longer
share a working copy at all. A caller that talks to `git-mcp-server` directly
and omits `workspaceKey` still gets the legacy shared-workspace behaviour
unchanged.

## Operations runbook: workflow agent-id reconciliation

This section documents the operational consequences of the workflow agent-id
reconciliation added to `embabel-agent-service`: a first-boot data repair of
persisted workflow records, a kill-switch property, the log lines to watch
for, two deliberate consequences of the repair, three new refusals at the
write and start boundaries, and a rollback hazard that is behaviour-safe but
not behaviour-identical.

### First boot after this change rewrites stale workflow records

On first boot after this change, `embabel-agent-service` inspects every
persisted `WorkflowDefinition` record in the `workflow-definitions` volume.
Only records whose `agentIds` is null or empty are rewritten; every other
record is left byte-identical. This is idempotent: once a record has been
repaired, subsequent boots make no further change to it — a re-run reconciles
zero additional records.

### What to grep for in the logs

The repair is performed by `WorkflowAgentIdsReconciler`. Two log lines are the
operational signal to watch for, both at INFO and verified against the merged
source:

- The summary line, logged once per boot: `Agent-id reconciliation: {}
workflow(s) inspected, {} changed` — the first number is every workflow
  record read (not only the eligible ones), the second is how many were
  actually rewritten.
- The per-record line, logged once for each record that is actually
  rewritten: `Reconciled agentIds for workflow '{}': before={}, after={},
rule={}` — where `rule` is one of `SEED_RESYNC`, `CATALOGUE_BACKFILL`, or
  `CATALOGUE_BACKFILL_GATE_FALLBACK`.

Copy-pasteable example:

```
docker compose logs embabel-agent-service | grep "Agent-id reconciliation"
docker compose logs embabel-agent-service | grep "Reconciled agentIds"
```

### The `reconcile-agent-ids` kill switch

The property is `openjcockpit.workflow-definitions.reconcile-agent-ids`,
boolean, default `true`, configured in
`services/embabel-agent-service/src/main/resources/application.yml`.

- It is **independent** of `openjcockpit.workflow-definitions.seed-defaults` —
  seeding and reconciliation are separate switches.
- It is **deliberately not** exposed as a `docker-compose.yml` environment
  variable — there is no `${...}` override for it in Compose, by design.
- If disabled, any workflow record whose `agentIds` remains null/empty is
  never repaired, and such a workflow becomes unstartable: attempting to
  start it returns a `BLOCKED` result with a named, logged reason, rather than
  silently running every catalogue agent.
- There is no dry-run mode.

### Deliberate behaviour change: `wf-spec-realise` narrows from seven agents to six

In an environment holding a stale `wf-spec-realise` record with empty
`agentIds`, that workflow previously started and implicitly ran all seven
catalogue agents (including `requirement`). After this change, the reconciler
resyncs it to its current seed definition, which is six agents: `impact`,
`test-design`, `implementation`, `review`, `realisation`, `evidence` —
`requirement` no longer runs for it. **This is the single most user-visible
consequence of this change.** It is intentional and is release-noted here
rather than requiring proactive operator outreach.

### Deliberate visibility change: `wf-spec-init` now explicitly lists `realisation` among its agents

`wf-spec-init` is seeded by design with an empty `agentIds` list, so before
this change it already implicitly ran all seven catalogue agents, including
`realisation` (which writes code and opens pull requests) — this was
pre-existing, invisible behaviour. After this change, reconciliation makes
that explicit: `wf-spec-init`'s persisted record gains the full seven-agent
set. **This is a visibility change only, not a behaviour change:**
`wf-spec-init` ran `realisation` before this change exactly as it does after.
A correction to `wf-spec-init`'s intended agent set is tracked as a separate
follow-up requirement, not part of this change.

### New refusals at the write and start boundaries

1. Create/update/import of a workflow with an empty agent selection is now
   rejected with HTTP 400 and message: `"At least one pipeline agent must be
selected for workflow '<id>'."` (from `WorkflowDefinitionValidator`).
2. Starting a workflow with no configured agents now returns a `BLOCKED`
   result — not a silent all-agents run — with reason: `"Workflow '<id>' has
no pipeline agents configured; select at least one agent in Design →
Workflows."` (from `WorkflowExecutionService`).
3. Starting a workflow where every candidate agent is denied by policy now
   returns a `BLOCKED` result with reason: `"All pipeline agents of workflow
'<id>' were denied by policy; nothing to run."` (from
   `WorkflowExecutionService`).
4. `POST /api/agent-runs` with a null or empty `agentIds` in the request body
   now returns HTTP 400 with message `"agentIds must not be null or empty"`
   (from `AgentRunController`).

### Rollback hazard: redeploying an older image does not undo the reconciliation

If `embabel-agent-service` is redeployed to a version that predates this
change, and the `workflow-definitions` volume is left alone (not deleted),
rollback is behaviour-**safe** but **not** behaviour-**identical**:

- The `wf-spec-realise` six-agent narrowing described above is already
  persisted in the volume and is **not** undone by redeploying an older image
  — the older code simply reads the already-narrowed record.
- An older build's code still contains the old "empty `agentIds` means run
  every catalogue agent" path. Any workflow record that is still empty
  (because reconciliation never ran, or ran with the kill switch off) will
  again run every catalogue agent, **reopening the policy-enforcement gap
  this change closed.**

Rolling back must therefore be a conscious, deliberate decision, not a
routine revert, precisely because the data and the code can now disagree
about what "empty `agentIds`" used to mean.

### `docker compose down -v` remains destructive

`docker compose down -v` deletes the `workflow-definitions` volume (and any
other named volumes) and remains a destructive action requiring explicit
operator permission before use. Nothing in this change relaxes that rule.

`docker compose stop`/`start` and a plain `docker compose down` (without
`-v`) preserve the LiteLLM `ricky` admin account, the virtual key, and
usage history in the `litellm` Postgres database. `docker compose down -v`
additionally destroys the LiteLLM database state along with Keycloak state,
since the `postgres` service declares no separately-scoped named volume for
litellm data.

### Local, non-Docker runs do not see this

`WorkflowDefinitionProperties` defaults its storage path to
`${java.io.tmpdir}/embabel-workflow-definitions` when not running under
Compose (Compose instead sets `WORKFLOW_DEFINITIONS_PATH` to
`/tmp/embabel-workflow-definitions` inside the container, backed by a named
volume). Consequently, a local, non-Docker run of `embabel-agent-service`
starts from an empty/fresh directory and will not see whatever stale records
exist in the Compose deployment's volume — relevant when trying to reproduce
the reconciliation behaviour, or the pre-existing defect it fixes, by hand.

### What this change does not touch

- No Keycloak realm, client, role, or seed-user change.
- No functional change to `docker-compose.yml`.
- No new environment variable.
- No container image version pin change.
- No new metric (the existing `approval_gate_paused_runs` metric is unaffected).
- No healthcheck change.

## Known limitations and follow-ups

- **MCP transport pinned to SSE.** The MCP transport between `git-mcp-server`
  and `embabel-agent-service` is explicitly pinned to the older SSE protocol
  (`spring.ai.mcp.server.protocol: sse` in `git-mcp-server`'s
  `application.yml`), because Spring AI 2.0.1 changed its default server
  transport to a newer "Streamable HTTP" transport and the
  `embabel-agent-service` client side was not migrated to it in this delivery.
  Migrating both ends to the newer Streamable HTTP transport is a deliberate,
  named follow-up for a future delivery, not an oversight.
- **`git-mcp-server` remains unauthenticated.** This is a pre-existing
  condition predating this upgrade, not introduced by it. Hardening it (adding
  authentication) is out of scope for this delivery and is a standing, known
  risk to address in a separate piece of work.
- **No Docker Compose healthchecks for the Java services.** Currently only
  `postgres` has a Compose healthcheck; `ai-control-service`,
  `embabel-agent-service`, and `git-mcp-server` are instead verified via their
  `/actuator/health` endpoints. Adding Compose-level healthchecks for these
  three services would be a nice operational improvement, but it was
  deliberately not added during this delivery because it was not forced by the
  framework version bump; it is a candidate for a future, separate operational
  improvement.
- **Framework error-body `timestamp` notation changed.** Boot's own generic
  error response body (the fallback `{timestamp, status, error, message,
path}` shape returned by `BasicErrorController` for uncaught exceptions) now
  renders its `timestamp` field using `Z` UTC-suffix notation (e.g.
  `2026-09-05T06:28:01.995Z`) instead of the pre-upgrade `+00:00` notation
  (e.g. `2026-09-04T15:15:01.306+00:00`) — same instant, different notation,
  purely cosmetic. This field is not part of the documented
  `shared/contracts/openapi.yaml` `ApiErrorResponse` schema (which only has
  `code`/`message`), so it is outside the frozen contract surface, but any
  external consumer that parses this generic fallback error shape by exact
  string format (rather than as a standard ISO-8601 timestamp) would need to
  tolerate both notations.
- **`WWW-Authenticate` header value on 401 responses changed.** Both
  `ai-control-service` and `embabel-agent-service` now return
  `WWW-Authenticate: Bearer
resource_metadata="http://<host>/.well-known/oauth-protected-resource"` on
  unauthenticated requests, instead of the pre-upgrade bare
  `WWW-Authenticate: Bearer`. This is a Spring Security 7.1.1 feature (RFC
  9728 protected-resource metadata discovery), and the URL value is derived
  from the request's own `Host` header, so frontend/API consumers should not
  depend on the exact header value being the bare string `Bearer`. Note that
  the RFC 9728 metadata endpoint itself
  (`/.well-known/oauth-protected-resource`) that Spring Security 7.1.1
  auto-registers has been explicitly locked down to require authentication
  (via a dedicated higher-priority security filter chain on both services),
  since by default it would have introduced a new anonymous endpoint — this
  was found and fixed during this delivery's integration review, not left as
  a known limitation to defer.
- **No CI enforcement for the Playwright E2E suite yet.** `e2e/` (see
  "End-to-end tests (Playwright)" above) is a local and QA-agent-run gate
  only; a PR that only touches `e2e/` triggers no CI leg today. This is a
  named, accepted gap, not an oversight — CI enforcement is a stated
  follow-up for a later requirement.
- **A wrong or missing LiteLLM gateway key produces two gateway requests,
  not one.** Both are correctly rejected with 401, fail-closed, and no
  credential is ever leaked; this is bounded (`max-attempts=2`), not an
  unbounded retry loop. Root cause: the outer retry wrapper
  (`embabel-agent-api`'s `LlmDataBindingProperties`) is a `final`
  third-party class shared across all LLM routes, with no extension point
  to exclude authentication failures from its generic retry policy. This
  was investigated and found to have no clean fix confined to this
  delivery's scope, without either misusing an unrelated exception type or
  silently changing shared retry behavior for the unrelated
  OpenAI/`gpt41Mini` path. Accepted as a documented residual risk, not
  fixed. Follow-up: request an upstream `embabel-agent-api` enhancement (a
  predicate/exception-registration hook on `LlmDataBindingProperties`, or a
  per-route retry-template override point), or accept a scoped
  compensating control at the LiteLLM/infrastructure layer if this becomes
  operationally significant.
- **MCP tool-call fidelity through the LiteLLM gateway was not exercised
  end-to-end during manual verification.** The default/coding LLM's
  tool-calling behavior through the gateway (`ollama_chat` provider) is
  architecturally sound and was verified via static analysis of the
  provider's request/response transformation, but a real MCP tool call
  (e.g. a `git.branch`/`git.commit`/`pr.create` invocation) was not
  exercised through it, because doing so would require pushing to a real
  Git repository with real credentials — nothing was provisioned or
  authorized for that in the verification environment, consistent with
  this repository's own rule against unauthorized git mutations. Recovery
  if this proves broken in practice is the documented Revert B (direct
  host-Ollama routing) above. Follow-up: exercise a full multi-stage agent
  run with an MCP tool call through the gateway in a properly sandboxed
  environment before relying on this path for tool-heavy workflows.
  **Update:** the LLM-only path (a single-stage agent run's default/coding
  LLM call transiting the gateway to host Ollama, positively attributed to
  the expected virtual key and model) is now covered by an automated,
  precondition-gated Playwright check (`npm run e2e:llm` — see
  `e2e/README.md`'s "The agent ⇄ LiteLLM ⇄ Ollama live check"). MCP
  tool-call fidelity through the gateway remains uncovered, as stated above.

## Dependency updates (Renovate)

Renovate checks the repository's dependency files and opens a pull request for each available
update. It never merges anything itself — every update still goes through the normal review and
CI process before it can land on `main`.

**Where it runs from**

- Workflow: [`.github/workflows/renovate.yml`](.github/workflows/renovate.yml)
- Config: [`renovate-config.json`](renovate-config.json) (referenced via `RENOVATE_CONFIG_FILE`)

**When it runs**

- On a daily schedule (`cron: "0 8 * * *"`, 08:00 UTC).
- Manual on-demand runs (`workflow_dispatch`)

**What it scans**

Renovate auto-detects dependency files from its default managers — currently that means Maven
(`pom.xml` in each reactor module), npm (`apps/dashboard/package.json`), and GitHub Actions
(`.github/workflows/*.yml`). No manager list is restricted in `renovate-config.json`, so newly
added supported files are picked up automatically.

**Update strategy**

- `rebaseWhen: conflicted` — Renovate only rebases a PR branch when it has conflicts, to avoid
  unnecessary churn.
- No `packageRules` grouping is configured yet, so each dependency currently gets its own PR
  (one-PR-per-dependency is the default). Group patch/minor updates per ecosystem in
  `renovate-config.json` (`packageRules` with `matchUpdateTypes`) if PR volume becomes a problem.
- `prHourlyLimit: 5` caps how many PRs Renovate opens per hour, to avoid flooding the PR list.
- Major-version updates are not treated differently from minor/patch yet — review major bumps
  with extra care since they are more likely to be breaking.

**Automated checks required before merge**

Every Renovate PR is a normal PR against `main` and must pass whatever required status checks are
configured in the repository's branch protection rules for `main` (build, test, lint). Branch
protection is configured in GitHub repo settings, not in this repository's files — confirm the
required checks list there if you need to change what's enforced.

**Developer review and testing**

- `reviewers` and `assignees` in `renovate-config.json` are set to the developers who must review
  Renovate PRs; update that list when ownership changes.
- A developer must review the release notes/changelog for the bumped dependency, exercise the
  affected functionality locally where relevant, and only then approve.
- Branch protection prevents merging without that approval — Renovate cannot bypass it.

**How developers are notified**

Renovate PRs set the assigned/reviewer-requested developers from `renovate-config.json`, which
triggers GitHub's standard notifications (email/notification inbox) for a review request and an
assignment. All Renovate PRs also carry the `dependencies` label, so they're easy to filter in the
PR list regardless of notifications.

**Identifying a Renovate PR**

- Label: `dependencies`
- Author: the account behind the `RENOVATE_TOKEN` secret
- Title: Renovate's default `"Update <dependency> to <version>"` style title

**Pausing or disabling Renovate**

- Fastest: disable the scheduled trigger by removing/commenting the `schedule` block in
  `.github/workflows/renovate.yml`, or disable the workflow from the Actions tab (**Renovate** →
  **⋯** → **Disable workflow**). `workflow_dispatch` still works for an on-demand run afterwards.
- To pause without touching the workflow file, add `"enabled": false` to `renovate-config.json`
  (Renovate will still run but won't open new PRs) — useful for a temporary freeze.
- To stop a single noisy dependency, add it to an `ignoreDeps` list in `renovate-config.json`.

**Handling a failed or incompatible update**

- If required CI checks fail on a Renovate PR: leave the PR open, do not merge. Either fix the
  incompatibility in a follow-up commit on the same PR branch, or close the PR — Renovate will
  reopen/recreate it on the next run once a compatible version is available, unless the dependency
  is added to `ignoreDeps` in `renovate-config.json`.
- If an update is merged and later found to cause a regression: revert the merge commit, then add
  the dependency to `ignoreDeps` (or pin a max version with `matchPackageNames` +
  `allowedVersions`) until a fixed release is available.

## Continuous Integration

Every push and pull request is gated by path-scoped GitHub Actions workflows
under `.github/workflows/`:

| Workflow          | Triggers on changes to                                    | Runs                                                                                                               |
| ----------------- | --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `backend-ci.yml`  | `services/**`, root `pom.xml`, `shared/contracts/**`      | `mvn -B verify` for the full Maven reactor — build, unit/integration tests, and the JaCoCo 100% line-coverage gate |
| `frontend-ci.yml` | `apps/dashboard/**`, `apps/landing/**`                    | `npm ci`, `npm test` (dashboard only — `landing` has no test script yet) and `npm run build`, for both apps        |
| `infra-ci.yml`    | `docker-compose.yml`, `infrastructure/**`, `.env.example` | `docker compose config` against `.env.example`, to catch YAML/interpolation errors without starting any container  |

These three run only when their own paths change, so a dashboard-only PR
never waits on a Maven build and vice versa. `ci-summary.yml` is the
orchestrator: it always runs, decides which of the three legs apply to the
current diff, and fans the results into a single required check named
**`ci-summary`** — that's the one check to require in GitHub branch
protection (the three leg checks are conditionally skipped and would block
merges forever if required directly). None of these jobs start Postgres,
Keycloak, or OPA — module tests already use Testcontainers/mocks, and the
Compose check only validates static config.

This is CI only: no image publishing, no deployment. See
`docs/delivery/ci-pipeline/` for the full spec and implementation notes,
including the manual branch-protection step and known open items.

**Playwright is a local and QA-agent gate only — there is no GitHub Actions enforcement yet.** A
change confined to `e2e/` triggers none of the three legs above, and no fourth workflow exists for it.
This is a named, accepted gap, not a silent omission: the QA engineer agent runs the suite as part of
its independent gate today, and CI enforcement is a stated follow-up for a later requirement (see
`docs/delivery/playwright-e2e-testing/`).

## Workflow definition file logging

`embabel-agent-service` logs structured events around reading YAML workflow/agent/subagent/skill
definition files and the two file-based audit trails (`decision-logs`, `approval-decisions`), all
of which share the same underlying `YamlDefinitionStore`.

**Log keys and levels** (logger `nl.metafactory.agents.workflow.YamlDefinitionStore`):

| Key                          | Level                                            | Meaning                                                      |
| ----------------------------- | ------------------------------------------------ | ------------------------------------------------------------ |
| `definition.file.read`         | DEBUG                                            | A single definition file was read successfully.               |
| `definition.file.read.failed`  | WARN                                              | A single definition file exists but failed to parse.           |
| `definition.dir.scanned`       | DEBUG when nothing failed, WARN when ≥1 file failed | Summary of a directory listing (scanned/loaded/failed counts). |
| `definition.dir.list.failed`   | WARN                                              | The definitions directory itself could not be listed.         |
| `definition.file.write.failed` | WARN                                              | A definition file could not be written to disk (save failure). |
| `definition.file.delete.failed`| WARN                                              | A definition file could not be deleted from disk (delete failure). |

The WARN-level lines carry the file or directory's **absolute** path, so they are easy to find with
a simple grep even though the corresponding public-facing exception messages only ever carry a
relative file name and a sanitised reason.

**Raising verbosity without a restart.** Two equivalent options:

- Call the already-exposed Spring Boot Actuator endpoint:
  `POST /actuator/loggers/nl.metafactory.agents.workflow.YamlDefinitionStore` with body
  `{"configuredLevel":"DEBUG"}`.
- Set the environment variable `LOGGING_LEVEL_NL_METAFACTORY_AGENTS_WORKFLOW_YAMLDEFINITIONSTORE=DEBUG`
  before starting the service.

**Tolerate-and-skip behaviour change.** A malformed or empty `.yaml` file inside a definitions
directory is now skipped (logged, not returned) instead of failing the entire directory listing
with a 500. This applies to every repository built on the shared store, including the two audit
trails: `decision-logs` (policy decision audit) and `approval-decisions` (approval decision audit).
A corrupt audit record therefore becomes silently absent from an audit listing — visible only in
the server log via `definition.file.read.failed`, never in any API response.

**Downstream impact on `ai-control-service`.** `GET /api/workflows` on `ai-control-service` can now
return a non-2xx `502 Bad Gateway` response carrying the upstream failure message, whereas
previously it always returned `200` with a possibly-empty array. API clients must distinguish a
`502` from a `200` with an empty array — the former means the listing failed, the latter means
there are genuinely no workflows.

**Boot behaviour.** If a seeded workflow id addresses a malformed definition file, the startup
importer that seeds workflows fails deterministically at boot (this component is deliberately
unchanged by this feature). The `definition.file.read.failed` WARN naming the absolute path is
emitted before that failure, and the resulting exception names the relative file, aiding diagnosis.

**A skipped file is a log-only signal.** Its exclusion from a listing is never itself surfaced as
response data to any API client.
