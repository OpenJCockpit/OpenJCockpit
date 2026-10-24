# Historical notes

This page preserves documentation from the former root README that describes behaviour which **no
longer matches the current implementation**. It is kept for traceability: for operators of older
deployments, for reviewers comparing versions, and so that no previously documented knowledge is lost.

> **Do not use this page to operate the current version.** Each section below names what replaced it.
> The text is preserved as it was written (headings demoted one level; one broken path corrected).

## Contents

- [Built-in spec pipeline: default workflows, realisation and deterministic Git publishing](#built-in-spec-pipeline-superseded)
- [Error-message disclosure history (D-QA-1 remediation and framework upgrade)](#error-message-disclosure-history-d-qa-1-remediation-and-framework-upgrade)
- [Operations runbook: workflow agent-id reconciliation](#operations-runbook-workflow-agent-id-reconciliation)

---

## Built-in spec pipeline (superseded)

**Superseded by:** catalogue-agent workflows and git-workspace runs, introduced with the Agent2Agent
(A2A) integration (commit `f5feb1b`, 2026-10-16). The built-in pipeline agents (requirement, impact, test
design, implementation, review, realisation, evidence), `CodeRealisationService`/`CodeRealisationAgent`,
`SpecGitPublisher`, `GitToolClient` and the `openjcockpit.spec-git` configuration (`SPEC_GIT_*`) no longer
exist in the code. The default workflows now chain the seeded catalogue agents, and Git delivery happens
through per-run workspaces with checkpoint commits.

Current documentation: [Agents and workflows](../workflows/agents-and-workflows.md),
[Git integration](../workflows/git-integration.md).

### Default workflows (automatically imported)

On startup, the embabel-agent-service automatically imports a default
workflow bundle (`spec-workflow/default-workflows.json`) with the group
**Spec-driven development** and four workflows:

| Workflow                      | Id                  | Purpose                                                                                                                                                                                                                                                        |
| ----------------------------- | ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Spec folder initialization    | `wf-spec-init`      | Pushes `.specify/templates/spec-template.md` as a pull request so agentic workflows can run against the repository.                                                                                                                                            |
| Create spec from prompt (RAG) | `wf-spec-create`    | Creates small, executable spec files in `specs/` based on a prompt and the repository content (RAG).                                                                                                                                                           |
| Implement spec                | `wf-spec-implement` | Generates an implementation plan (architecture, changes, test plan, review) for the selected spec file and publishes it as `specs/implementation-<run-id>.md` on a dedicated `impl/` branch with a pull request. Delivers **no code changes** — only the plan. |
| Realize spec (code)           | `wf-spec-realise`   | Actually carries out the implementation: see below.                                                                                                                                                                                                            |

#### Realize spec (code) — the agent that does the real work

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

#### Spec and implementation publishing to git (deterministic)

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

---

## Error-message disclosure history (D-QA-1 remediation and framework upgrade)

**Superseded by:** the current mechanism described in
[Approval gates, behaviour change 3](../governance/approval-gates.md#deliberate-behaviour-change-3-embabel-agent-service-includes-exception-messages-in-its-http-error-responses-service-wide).
The user-visible behaviour (error responses include a `message` field, service-wide) is unchanged.

### Deliberate behaviour change 3 (original text): `embabel-agent-service` now includes exception messages in its HTTP error responses, service-wide

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

---

## Operations runbook: workflow agent-id reconciliation

**Superseded by:** the catalogue-agent workflow model. `WorkflowAgentIdsReconciler` and the
`openjcockpit.workflow-definitions.reconcile-agent-ids` property no longer exist in the code; workflow
agent references are now stored in the `workflow_agent_refs` table, and an empty agent selection is
rejected at save time and blocked at start time (see
[refusals](../workflows/agents-and-workflows.md#refusals-at-the-write-and-start-boundaries)). The
validation and refusal messages quoted below are the historical wording. The warnings about
`docker compose down -v` and non-Docker storage paths remain valid and are maintained in
[Runbooks](../operations/runbooks.md).

### Original runbook text

This section documents the operational consequences of the workflow agent-id
reconciliation added to `embabel-agent-service`: a first-boot data repair of
persisted workflow records, a kill-switch property, the log lines to watch
for, two deliberate consequences of the repair, three new refusals at the
write and start boundaries, and a rollback hazard that is behaviour-safe but
not behaviour-identical.

#### First boot after this change rewrites stale workflow records

On first boot after this change, `embabel-agent-service` inspects every
persisted `WorkflowDefinition` record in the `workflow-definitions` volume.
Only records whose `agentIds` is null or empty are rewritten; every other
record is left byte-identical. This is idempotent: once a record has been
repaired, subsequent boots make no further change to it — a re-run reconciles
zero additional records.

#### What to grep for in the logs

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

#### The `reconcile-agent-ids` kill switch

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

#### Deliberate behaviour change: `wf-spec-realise` narrows from seven agents to six

In an environment holding a stale `wf-spec-realise` record with empty
`agentIds`, that workflow previously started and implicitly ran all seven
catalogue agents (including `requirement`). After this change, the reconciler
resyncs it to its current seed definition, which is six agents: `impact`,
`test-design`, `implementation`, `review`, `realisation`, `evidence` —
`requirement` no longer runs for it. **This is the single most user-visible
consequence of this change.** It is intentional and is release-noted here
rather than requiring proactive operator outreach.

#### Deliberate visibility change: `wf-spec-init` now explicitly lists `realisation` among its agents

`wf-spec-init` is seeded by design with an empty `agentIds` list, so before
this change it already implicitly ran all seven catalogue agents, including
`realisation` (which writes code and opens pull requests) — this was
pre-existing, invisible behaviour. After this change, reconciliation makes
that explicit: `wf-spec-init`'s persisted record gains the full seven-agent
set. **This is a visibility change only, not a behaviour change:**
`wf-spec-init` ran `realisation` before this change exactly as it does after.
A correction to `wf-spec-init`'s intended agent set is tracked as a separate
follow-up requirement, not part of this change.

#### New refusals at the write and start boundaries

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

#### Rollback hazard: redeploying an older image does not undo the reconciliation

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

#### `docker compose down -v` remains destructive

`docker compose down -v` deletes the `workflow-definitions` volume (and any
other named volumes) and remains a destructive action requiring explicit
operator permission before use. Nothing in this change relaxes that rule.

`docker compose stop`/`start` and a plain `docker compose down` (without
`-v`) preserve the LiteLLM `ricky` admin account, the virtual key, and
usage history in the `litellm` Postgres database. `docker compose down -v`
additionally destroys the LiteLLM database state along with Keycloak state,
since the `postgres` service declares no separately-scoped named volume for
litellm data.

#### Local, non-Docker runs do not see this

`WorkflowDefinitionProperties` defaults its storage path to
`${java.io.tmpdir}/embabel-workflow-definitions` when not running under
Compose (Compose instead sets `WORKFLOW_DEFINITIONS_PATH` to
`/tmp/embabel-workflow-definitions` inside the container, backed by a named
volume). Consequently, a local, non-Docker run of `embabel-agent-service`
starts from an empty/fresh directory and will not see whatever stale records
exist in the Compose deployment's volume — relevant when trying to reproduce
the reconciliation behaviour, or the pre-existing defect it fixes, by hand.

#### What this change does not touch

- No Keycloak realm, client, role, or seed-user change.
- No functional change to `docker-compose.yml`.
- No new environment variable.
- No container image version pin change.
- No new metric (the existing `approval_gate_paused_runs` metric is unaffected).
- No healthcheck change.
