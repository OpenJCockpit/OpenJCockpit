# Documentation migration map

The root `README.md` used to contain all technical and operational documentation (1 322 lines). It is
now a product landing page, and its content has moved into this knowledge base. This map lists **every
heading of the former README** and where its content lives now, so that preservation can be verified.

The former README is available in Git history (for example `git show d4885af:README.md`).

Legend: **Moved** = content preserved (wording kept, paths corrected); **Updated** = content preserved
and corrected against the current implementation; **Historical** = superseded content preserved verbatim
in [Historical notes](historical-notes.md), with the current behaviour documented elsewhere.

## Section-by-section map

| # | Former README heading | Status | New location |
| --- | --- | --- | --- |
| 1 | `# OpenJCockpit + Keycloak + Custom Login Theme` (title, logo, tagline) | Updated | Root [`README.md`](../../../README.md) hero |
| 2 | `## What is in this project?` | Moved | [Overview → Original feature list](../concepts/overview.md#original-feature-list); dashboard description in [Overview → The AI Orchestration Dashboard](../concepts/overview.md#the-ai-orchestration-dashboard) |
| 3 | `## Starting the application with Docker Compose` | Moved | [Quickstart](../getting-started/quickstart.md) |
| 4 | `### Local Ollama Qwen model (prerequisite, before you start the stack)` | Moved | [LLM providers → Local Ollama Qwen model](../integrations/llm-providers.md#local-ollama-qwen-model-prerequisite-before-you-start-the-stack) |
| 5 | `### LiteLLM gateway` (variables, verification, warnings, durations) | Updated | [LLM providers → LiteLLM gateway](../integrations/llm-providers.md#litellm-gateway) |
| 5a | ↳ Revert A / Revert B | Moved + verification note | [LLM providers → Reverting the default LLM](../integrations/llm-providers.md#reverting-the-default-llm) |
| 5b | ↳ Release note / Release note (LiteLLM gateway) | Moved | [LLM providers → Release notes](../integrations/llm-providers.md#release-notes) |
| 5c | ↳ `cp .env.example .env` … URLs, login redirect | Moved | [Quickstart → Configure and start](../getting-started/quickstart.md#2-configure-and-start), [Open the dashboard](../getting-started/quickstart.md#3-open-the-dashboard) |
| 6 | `## Keycloak data` | Moved | [Authentication → Keycloak data](../getting-started/authentication.md#keycloak-data) |
| 7 | `## Custom login theme` | Updated | [Authentication → Custom login theme](../getting-started/authentication.md#custom-login-theme) |
| 8 | `## Re-importing the realm` (incl. landing-app note) | Moved + warning | [Authentication → Re-importing the realm](../getting-started/authentication.md#re-importing-the-realm) |
| 9 | `## Local frontend development` | Updated | [Local development → Local frontend development](../development/local-development.md#local-frontend-development) |
| 10 | `## End-to-end tests (Playwright)` | Updated | [Testing → End-to-end tests](../development/testing.md#end-to-end-tests-playwright) |
| 11 | `## Spec queue` | Moved | [Spec queue](../workflows/spec-queue.md) |
| 12 | `### How to use it` | Moved | [Spec queue → How to use it](../workflows/spec-queue.md#how-to-use-it) |
| 13 | `### Configuration` | Moved | [Spec queue → Configuration](../workflows/spec-queue.md#configuration) |
| 14 | `### The openjcockpit-admin role` | Moved | [Spec queue → The openjcockpit-admin role](../workflows/spec-queue.md#the-openjcockpit-admin-role), [Authentication → Roles](../getting-started/authentication.md#roles) |
| 15 | `### GitHub token scopes` | Moved | [Spec queue → GitHub token scopes](../workflows/spec-queue.md#github-token-scopes) |
| 16 | `### Residual risks` | Moved + added risk | [Spec queue → Residual risks](../workflows/spec-queue.md#residual-risks) |
| 17 | `### Rollback` | Moved | [Spec queue → Rollback](../workflows/spec-queue.md#rollback) |
| 18 | `## Important` (plaintext realm passwords) | Moved | [Authentication](../getting-started/authentication.md) (top), [Security considerations](../governance/security-considerations.md#local-only-credentials-and-secrets) |
| 19 | `## MCP: central tools for agents (git-mcp-server)` | Updated | [MCP tools](../integrations/mcp-tools.md) |
| 20 | `### Per-run workspace isolation (workspaceKey)` | Moved | [MCP tools → Per-run workspace isolation](../integrations/mcp-tools.md#per-run-workspace-isolation-workspacekey) |
| 21 | `## Default workflows (automatically imported)` | Updated + Historical | Current: [Agents and workflows → Default workflows](../workflows/agents-and-workflows.md#default-workflows-automatically-imported). Original: [Historical notes](historical-notes.md#default-workflows-automatically-imported) |
| 22 | `### Realize spec (code) — the agent that does the real work` | Historical | [Historical notes](historical-notes.md#realize-spec-code--the-agent-that-does-the-real-work); current behaviour in [Agents and workflows](../workflows/agents-and-workflows.md) and [Git integration](../workflows/git-integration.md) |
| 22a | ↳ idempotent import, `seed-defaults` | Moved | [Agents and workflows → Default workflows](../workflows/agents-and-workflows.md#default-workflows-automatically-imported) |
| 22b | ↳ spec-init / create-spec dashboard flows | Moved | [Spec-driven development → Spec-init and create flows](../workflows/spec-driven-development.md#spec-init-and-create-flows-in-the-dashboard) |
| 22c | ↳ `promptRequired`, `promptInstructions` | Moved | [Agents and workflows → Prompt-driven workflows](../workflows/agents-and-workflows.md#prompt-driven-workflows) |
| 23 | `### Spec and implementation publishing to git (deterministic)` | Historical | [Historical notes](historical-notes.md#spec-and-implementation-publishing-to-git-deterministic); current: [Git integration](../workflows/git-integration.md) |
| 24 | `## Workflow Design: groups and JSON export/import` | Moved | [Agents and workflows → Workflow Design](../workflows/agents-and-workflows.md#workflow-design-groups-and-json-exportimport) |
| 25 | `## Spec-driven development workflow` (rules, init) | Moved | [Spec-driven development](../workflows/spec-driven-development.md#spec-driven-workflow-for-openjcockpits-agentic-development) |
| 25a | ↳ Backend and frontend verification commands | Moved | [Local development → Verification](../development/local-development.md#verification) |
| 26 | `## Local Skills Marketplace double (opt-in, dev-only)` | Moved | [Skills Hub](../integrations/skills-marketplace.md) |
| 27 | `### Starting it` | Moved | [Skills Hub → Starting it](../integrations/skills-marketplace.md#starting-it) |
| 28 | `### SKILLS_MARKETPLACE_* environment variables` | Moved | [Skills Hub → variables](../integrations/skills-marketplace.md#skills_marketplace_-environment-variables) |
| 29 | `### Trying it out against the mock` | Moved | [Skills Hub → Trying it out](../integrations/skills-marketplace.md#trying-it-out-against-the-mock) |
| 30 | `### Warning: SKILLS_MARKETPLACE_ALLOW_PRIVATE_EGRESS …` | Moved | [Skills Hub → Warning](../integrations/skills-marketplace.md#warning-skills_marketplace_allow_private_egress-must-never-be-set-in-any-deployed-environment), [Security considerations](../governance/security-considerations.md) |
| 31 | `### Known limitation: double /skills …` | Moved | [Skills Hub → Known limitation](../integrations/skills-marketplace.md#known-limitation-double-skills-if-the-stored-url-already-ends-in-skills) |
| 32 | `## Operations runbook: human-in-the-loop workflow approval gate` | Updated | [Approval gates → Operations runbook](../governance/approval-gates.md#operations-runbook) |
| 33 | `### The three tunables` | Moved | [Approval gates → The three tunables](../governance/approval-gates.md#the-three-tunables) |
| 34 | `### Workflow-trigger tunables (workflow chaining)` | Moved | [Agents and workflows → Workflow chaining](../workflows/agents-and-workflows.md#workflow-chaining-workflow-orbs) |
| 34a | ↳ "`APPROVAL_GATE_MAX_FEEDBACK_ITERATIONS` is effectively the only cost control" | Moved | [Approval gates → The three tunables](../governance/approval-gates.md#the-three-tunables) |
| 35 | `### Deliberate behaviour change 1: unknown run ids …` | Moved | [Approval gates](../governance/approval-gates.md#deliberate-behaviour-change-1-unknown-run-ids-now-surface-as-run-state-lost-not-an-endless-waiting-poll) |
| 36 | `### Deliberate behaviour change 2: "no changes produced" …` | Moved | [Approval gates](../governance/approval-gates.md#deliberate-behaviour-change-2-no-changes-produced-is-no-longer-reported-as-a-failure) |
| 37 | `### Deliberate behaviour change 3: … exception messages …` | Updated + Historical | Current: [Approval gates](../governance/approval-gates.md#deliberate-behaviour-change-3-embabel-agent-service-includes-exception-messages-in-its-http-error-responses-service-wide). Original text and both "Update" paragraphs: [Historical notes](historical-notes.md#error-message-disclosure-history-d-qa-1-remediation-and-framework-upgrade) |
| 38 | `### Rollback hazard: removing a saved approvalGate: configuration …` | Moved | [Approval gates → Rollback hazard](../governance/approval-gates.md#rollback-hazard-removing-a-saved-approvalgate-configuration-before-rolling-back-past-this-feature) |
| 39 | `### In-flight paused runs do not survive a restart or redeploy` | Moved | [Approval gates](../governance/approval-gates.md#paused-runs-survive-a-restart-or-redeploy) |
| 40 | `### No new authentication or authorization role is introduced` | Updated | [Approval gates](../governance/approval-gates.md#no-new-authentication-or-authorization-role-is-introduced) |
| 41 | `### Resolved: per-run workspace isolation replaces …` | Moved | [MCP tools → Resolved](../integrations/mcp-tools.md#resolved-per-run-workspace-isolation-replaces-the-one-shared-git-working-copy-per-repository) |
| 42 | `## Operations runbook: workflow agent-id reconciliation` | Historical | [Historical notes](historical-notes.md#operations-runbook-workflow-agent-id-reconciliation) |
| 43 | `### First boot after this change rewrites stale workflow records` | Historical | [Historical notes](historical-notes.md#first-boot-after-this-change-rewrites-stale-workflow-records) |
| 44 | `### What to grep for in the logs` | Historical | [Historical notes](historical-notes.md#what-to-grep-for-in-the-logs) |
| 45 | `### The reconcile-agent-ids kill switch` | Historical | [Historical notes](historical-notes.md#the-reconcile-agent-ids-kill-switch) |
| 46 | `### Deliberate behaviour change: wf-spec-realise narrows from seven agents to six` | Historical | [Historical notes](historical-notes.md#deliberate-behaviour-change-wf-spec-realise-narrows-from-seven-agents-to-six) |
| 47 | `### Deliberate visibility change: wf-spec-init now explicitly lists realisation …` | Historical | [Historical notes](historical-notes.md#deliberate-visibility-change-wf-spec-init-now-explicitly-lists-realisation-among-its-agents) |
| 48 | `### New refusals at the write and start boundaries` | Historical + Updated | Original wording: [Historical notes](historical-notes.md#new-refusals-at-the-write-and-start-boundaries). Current messages: [Agents and workflows → Refusals](../workflows/agents-and-workflows.md#refusals-at-the-write-and-start-boundaries) |
| 49 | `### Rollback hazard: redeploying an older image does not undo the reconciliation` | Historical | [Historical notes](historical-notes.md#rollback-hazard-redeploying-an-older-image-does-not-undo-the-reconciliation) |
| 50 | `### docker compose down -v remains destructive` | Moved (also kept in historical) | [Runbooks](../operations/runbooks.md#docker-compose-down--v-remains-destructive), [LLM providers](../integrations/llm-providers.md#litellm-data-and-docker-compose-down--v) |
| 51 | `### Local, non-Docker runs do not see this` | Updated (also kept in historical) | [Runbooks → Local, non-Docker runs](../operations/runbooks.md#local-non-docker-runs-use-a-different-storage-path) |
| 52 | `### What this change does not touch` | Historical | [Historical notes](historical-notes.md#what-this-change-does-not-touch) |
| 53 | `## Known limitations and follow-ups` | Updated | [Known limitations](../operations/known-limitations.md) |
| 54 | `## Dependency updates (Renovate)` | Updated (corrected) | [Dependency management](../operations/dependency-management.md) |
| 55 | `## Continuous Integration` | Updated | [Continuous integration](../development/continuous-integration.md) |
| 56 | `## Workflow definition file logging` | Moved | [Runbooks → Workflow definition file logging](../operations/runbooks.md#workflow-definition-file-logging) |

## Corrections made during migration

Content was verified against the code and configuration at commit `d4885af`. The following former
statements were outdated or incorrect and have been corrected in the new pages:

| Former statement | Correction | Evidence |
| --- | --- | --- |
| Realm file at `keycloak/openjcockpit-realm.json`; theme at `themes/openjcockpit/login`; mount `./themes:/opt/keycloak/themes:ro` | `infrastructure/keycloak/openjcockpit-realm.json`, `infrastructure/themes/openjcockpit/login`, mount `./infrastructure/themes:/opt/keycloak/themes:ro` | `docker-compose.yml` |
| Vite dev server "usually http://localhost:5173" | Dashboard Vite dev server runs on port `4000` (5173 remains a registered redirect URI) | `frontend/apps/dashboard/vite.config.ts` |
| Links to `docs/dashboard-feature-libraries.md`, `docs/delivery/ci-pipeline/`, `docs/delivery/playwright-e2e-testing/` | These files do not exist; links replaced by library READMEs, or noted as missing | Repository tree |
| `e2e/README.md` | `frontend/e2e/README.md` | Repository tree |
| Default workflows run built-in pipeline agents; *Implement spec* delivers only a plan; *Realise spec* writes code and opens a PR via `SpecGitPublisher` | Default workflows chain seeded catalogue A2A agents; runs on a project push a branch with per-agent checkpoints; no automatic PR | `default-workflows.json`, `OPERATIONS.md` §9b, removed classes |
| Seven MCP Git tools; "one managed workspace clone per repository" | Nine tools (`git_list_files`, `git_read_file` added); per-run/per-job workspaces; server reached through LiteLLM, not published on the host; credentials server-side | `git-mcp-server` sources, `docker-compose.yml` |
| Agent-id reconciliation runbook (`WorkflowAgentIdsReconciler`, `reconcile-agent-ids`) | No longer in the code; moved to historical notes | Source tree |
| Validation message `"At least one pipeline agent must be selected…"` and `POST /api/agent-runs` refusal | Current: `"At least one agent must be linked to workflow '<id>'."`; `POST /api/agent-runs` was removed | `WorkflowDefinitionValidator`, `OPERATIONS.md` §9b |
| "Only `postgres` has a Compose healthcheck" | `postgres`, `keycloak` and `litellm` have healthchecks; the Java services still do not | `docker-compose.yml` |
| "A PR that only touches `e2e/` triggers no CI leg"; "no fourth workflow exists" | A non-blocking, informational `e2e-ci.yml` leg exists; it is not part of the required `ci-summary` check | `.github/workflows/e2e-ci.yml` |
| Renovate "never merges anything itself", runs daily at 08:00 UTC, no `packageRules` grouping | Hourly workflow gated by a weekly schedule; grouped PRs; patch/digest, lock-file maintenance and vulnerability updates are auto-merged once CI passes | `renovate-config.json`, `.github/workflows/renovate.yml` |
| Intro animation uses the surname | Uses the first name | `frontend/libs/landing-hub/src/IntroGreeting/IntroGreeting.tsx` |
| "Three default workflows from the global group" | Four default workflows | `default-workflows.json` |

## Discrepancies flagged, not resolved

These were found during migration and are documented as warnings rather than silently "fixed":

- **Revert A/B via `.env`**: `docker-compose.yml` does not pass `LLM_CODING_ROUTE` or
  `EMBABEL_MODELS_DEFAULT_LLM` into `embabel-agent-service`, so setting them in `.env` alone does not
  appear to reach the container. See [LLM providers](../integrations/llm-providers.md#reverting-the-default-llm).
- **Direct MCP client**: Compose sets `MCP_CLIENT_ENABLED=true`, while `OPERATIONS.md` §9b says the direct
  MCP client is switched off. See [MCP tools](../integrations/mcp-tools.md#central-mcp-client-remotemcptoolexecutor).
- **Spec queue auto-merge**: the runner needs a `pull-request:` run artifact that no current default
  workflow emits. See [Spec queue](../workflows/spec-queue.md#residual-risks).
- **LiteLLM image version**: `infrastructure/litellm/README.md` and `OPERATIONS.md` mention `v1.101.2`;
  `docker-compose.yml` pins `v1.101.6`.

## Anchor compatibility

Former README anchors (for example `#spec-queue`, `#known-limitations-and-follow-ups`) no longer exist in
the root README. The new root README links to the new pages, and this map gives the destination of every
former section.
