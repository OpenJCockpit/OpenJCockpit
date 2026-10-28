# Human-in-the-loop approval gates

An **approval gate** pauses a workflow run after a chosen agent and asks a human to decide before the
run continues. The dashboard shows the gate's context (including the files the agent changed) and lets
the reviewer:

- **Accept**: the run continues.
- **Deny**: the run ends.
- **Accept with comments**: the anchored agent runs again with the reviewer's feedback and its previous
  output, then the gate opens again (bounded, see below).

Gates are configured per workflow in Workflow Design and are **anchored to an agent reference** in that
workflow. Decisions are recorded durably in an approval audit trail. The default workflows have no gate
configured; add one where you want a human decision.

> **Terminology.** This runbook was written when workflows ran a built-in pipeline whose code-writing
> stage was called `realisation`. Workflows now consist of catalogue agents, and a gate's placement
> "stage" is an agent reference (for example `a2a:litellm:software-developer`). Where the text below says
> "the `realisation` stage", read "the agent the gate is anchored to".

## Operations runbook

This section documents the operational consequences of the workflow approval gate
feature in `embabel-agent-service`: three configuration tunables, three deliberate
behaviour changes, a rollback hazard that requires a manual step, and operating
characteristics an operator should be aware of before restarting or
redeploying the service.

### The three tunables

The `embabel-agent-service` service in `docker-compose.yml` exposes three
environment-overridable tunables, following the project's existing
`${VAR:-default}` convention:

| Variable                                | Default | What it bounds                                                                                                                                                                                                               |
| --------------------------------------- | ------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `APPROVAL_GATE_MAX_FEEDBACK_ITERATIONS` | `3`     | The maximum number of "accept with comments" feedback loops allowed at a single approval gate (i.e. at most 4 gate openings per run: the initial one plus up to 3 feedback iterations). |
| `APPROVAL_GATE_COMMENT_MAX_LENGTH`      | `4000`  | The maximum length, in characters, of the free-text comment an operator can submit with an "accept with comments" decision.                                                                                                  |
| `APPROVAL_GATE_MAX_CHANGED_PATHS`       | `50`    | The maximum number of changed file paths shown in the approval context payload before the remainder are summarized as "N more files".                                                                                        |

**`APPROVAL_GATE_MAX_FEEDBACK_ITERATIONS` is effectively the only cost control this
feature has.** Every time an operator presses "accept with comments" on an approval
gate, the system re-runs the anchored agent (at the time this was written: the full
`realisation` stage, roughly two additional LLM calls plus a full repository clone/read
cycle, followed by an additional commit pushed to the run's branch). There is no other
cost meter, rate limit, or token budget anywhere in this platform. Left unbounded, an
operator could multiply a single run's LLM and git-push cost simply by repeatedly
requesting revisions. Lowering this value is the only lever available to cap that cost;
there is no alternative safeguard to fall back on.

The related workflow-chaining tunables (`WORKFLOW_TRIGGER_*`) are documented in
[Agents and workflows](../workflows/agents-and-workflows.md#workflow-chaining-workflow-orbs).

### Deliberate behaviour change 1: unknown run ids now surface as "run state lost", not an endless "waiting" poll

Before this feature, polling the status of an unknown or expired run id returned
an HTTP 200 with a status value the dashboard did not recognize. The dashboard
does not treat unrecognized statuses as terminal, so it kept polling forever and
showed a "waiting" indicator that never resolved: a pre-existing bug.

This feature fixes that: any unknown run id (for example, after a service
restart, or a stale bookmark, or a mistyped run id) now surfaces as a clear,
terminal "run state lost" outcome in the dashboard. The dashboard stops polling
and offers the operator a way to start the workflow again.

This applies to **every** unknown run id, not only runs that were paused at an
approval gate. It is worth calling out explicitly because it changes visible
dashboard behaviour broadly, including for runs and workflows that never use
the approval gate feature at all, not just for approval-gate scenarios.

### Deliberate behaviour change 2: "no changes produced" is no longer reported as a failure

Previously, when a code-generation (`realisation`) stage produced no changes,
either because the agent decided nothing needed changing, or because there was
genuinely nothing new to commit, the run was reported as a technical failure.

This feature changes that: a no-op outcome is now reported as its own distinct
"no change" outcome, separate from a technical failure. If the workflow has an
approval gate configured, the gate still opens normally on this outcome so a
human can review and decide (accept, deny, or, where the gate supports it,
request a further iteration) rather than the run looking like it errored.

Runs on workflows **without** an approval gate configured behave exactly as
before this change: a "no changes" outcome does not surprise
an operator with new status values they have not seen previously in that
scenario.

### Deliberate behaviour change 3: `embabel-agent-service` includes exception messages in its HTTP error responses, service-wide

To let the approval gate's validation error messages actually reach the
frontend, error responses include the exception `message` field.

This setting is **not scoped to the approval-gate endpoints**: it applies to
**all** endpoints in `embabel-agent-service` (and `ai-control-service` uses the
same setting). As a result, any unhandled server-side error anywhere in that service
will surface its exception message to an authenticated caller, where previously that
message would have been suppressed from the HTTP response body.

This is a deliberate, accepted trade-off for this feature, not a bug. It is a
mild, service-wide widening of information disclosure: operators should be
aware that error responses from `embabel-agent-service` may be more
verbose and informative than before this feature, across the whole service,
not only for approval-gate scenarios.

**Current mechanism (Spring Boot 4.1.1 / Spring AI 2.0.1 / Embabel 1.5.1):** the property that
controls whether the `message` field appears in error responses is
`spring.web.error.include-message: always`; the older `server.error.include-message` is inert for
this purpose. Both `embabel-agent-service` and `ai-control-service` use
`spring.web.error.include-message: always` consistently, and Spring Boot's own default
`BasicErrorController` is active, untouched. The history of how this setting was first a no-op and
then required a workaround bean (`ErrorHandlingConfig`) that has since been removed is preserved in
[Historical notes](../reference/historical-notes.md#error-message-disclosure-history-d-qa-1-remediation-and-framework-upgrade).

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

**This hazard is narrower than it might sound. Do not over-apply this
warning:**

- Rolling back **only the frontend** (`frontend/apps/dashboard`) is fine and needs no
  special step.
- Rolling back **only the backend**, to a version that already includes this
  feature's initial groundwork (i.e. a version that can already parse
  `approvalGate:`, even if earlier than the final feature), is fine and needs
  no special step.
- The hazard applies **only** to a rollback of `embabel-agent-service` to a
  version that predates the whole feature, i.e. one whose code has never heard
  of the `approvalGate:` field.

### Paused runs survive a restart or redeploy

A run that is sitting at an approval gate, waiting for a human decision, is
stored in the database (table `agent_parked_runs`, Flyway `V7`) at the moment
the gate opens, in the same transaction that records the run as
`AWAITING_APPROVAL`. It survives a restart or redeploy of
`embabel-agent-service`:

- **Startup.** The reconciler marks only runs that were genuinely in flight
  (non-terminal and **not** parked at a gate) as "run state lost". The
  rehydrator then re-adopts every parked run: the run is live again with status
  `AWAITING_APPROVAL`, the gate context can be opened, and Accept, Accept with
  comments, Deny and Stop work exactly as before the restart.
- **What is restored.** Everything the resumed run reads: the start request,
  the workflow input, which agents already ran and their outputs, started
  workflow orbs, and the full gate state (iteration, decided iterations,
  comment history, last change report, retained branch and pull-request URL).
  The run's scope is registered again (workflow id, run id and, for runs on a
  project, the git workspace binding: workspace key, repository and branch),
  so the agents of the resumed run are attributed to the run for tool-call
  policy and use the same git workspace as before.
- **Exactly one decision wins.** A decision claims the parked run with a
  row-count-checked database delete (run id + gate iteration + version). Of
  any number of simultaneous decisions, in one instance or several instances
  over the same database, exactly one resumes the run; the others get `409`.
- **A gate that cannot be restored fails, it never hangs.** If a stored
  snapshot cannot be read (corrupt, or written in an unsupported format), the
  run is marked `FAILED` with the persisted reason "Approval gate state could
  not be restored after a restart; the run cannot be resumed" (also recorded
  as a run event). A stored gate for a run that already finished is discarded.

Limits:

- **Runs that were executing** an agent (not waiting at a gate) when the
  service stopped are not resumed; they show "run state lost" and must be
  started again. The same applies to a run waiting for a child workflow
  (`AWAITING_CHILD_WORKFLOW`): that wait is still held in memory only.
- **The git token of the start request is not stored.** The engine does not
  use it (the git MCP server resolves its own credentials for the workspace),
  but a child workflow started by a workflow orb *after* a resumed gate gets no
  git token.
- **The git workspace itself lives in `git-mcp-server`.** Only its binding is
  stored. If that workspace no longer exists when the run resumes (for example
  `git-mcp-server` lost its volume), the checkpoint after each agent is
  recorded as failed ("git workspace unavailable") and the agents' git tools
  return errors; nothing waits for the missing workspace.
- **Git-workspace tickets** are single-use and consumed when the run starts;
  a resumed run does not need them. An *unredeemed* ticket is still lost on
  restart, and that start must be retried.
- The startup reconciler assumes a single `embabel-agent-service` instance: it
  marks every non-parked, non-terminal run as lost, including runs another
  live instance would still be executing.

Approval decisions themselves were already recorded durably (under the same
`workflow-definitions` volume the audit trail uses) and remain readable
afterwards. The parked-run count behind the `approval_gate_paused_runs` metric is read
from the database, so it is correct across restarts.

Also note that a git-workspace job follows its run for at most `WORKFLOW_AGENT_TIMEOUT` (locally
3600 s), **including while a gate is waiting**, and then stops the run. See
[Git integration](../workflows/git-integration.md#known-limitations-of-workspace-runs).

### No new authentication or authorization role is introduced

Approving, denying, or commenting on an approval gate is available to any
authenticated user of the platform. This matches how every other feature in
this platform worked when the gate was introduced: there were no fine-grained user roles defined
anywhere in the Keycloak realm. (The `openjcockpit-admin` role was added later, for the spec queue's
auto-merge setting only.)

This is a deliberate, accepted posture for this delivery, not an oversight.
Introducing proper role-based restriction on who may approve pipeline changes
that write to a customer repository is flagged as valuable future work, and is
explicitly not addressed here. No Keycloak realm, client, or role configuration
was changed as part of this feature.

### Resolved: per-run workspace isolation

The shared-working-copy limitation that the "accept with comments" loop made more noticeable has been
resolved by per-run workspace isolation. See
[MCP tools](../integrations/mcp-tools.md#resolved-per-run-workspace-isolation-replaces-the-one-shared-git-working-copy-per-repository).

## Related

- [Policy enforcement](policy-enforcement.md)
- [Agents and workflows](../workflows/agents-and-workflows.md)
- [Known limitations](../operations/known-limitations.md)
