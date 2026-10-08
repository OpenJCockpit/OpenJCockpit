# embabel-agent-service

Spring Boot service that orchestrates the multi-agent workflow pipeline (requirement, impact,
test-design, implementation, review, realisation, evidence), exposes workflow and agent-run
history APIs, and persists per-run execution state durably in `agent_runs` (see
`src/main/resources/db/migration`).

## Log keys

| Key | Level | When it is emitted | Notes |
|---|---|---|---|
| `run.failure.recorded runId=… status=FAILED summaryLength=…` | INFO | Once per run, when a terminal `FAILED` status is flushed with a non-null, sanitised failure summary (see `RunFailureDiagnostics`). | Logs the **length** of the summary only, never the summary value itself — the summary is already sanitised for browser display, but logging its full text would still create a second, unnecessary disclosure surface with a different audience (log aggregation) than the one the sanitiser was designed for. Emitted at INFO because it is routine, expected operational signal, not an error condition in this service's own logs (the underlying exception is still logged in full, server-side only, at ERROR level via the pre-existing `log.error("Workflow pipeline failed for run {}", runId, e)` call in `EmbabelOrchestrator`). No verbosity flag gates it; it fires unconditionally whenever a run terminates as FAILED with a summary. |

## Workflow definition writes and the residual-race statement (BR-11)

workflow-execution-state-to-database removes the read/write-state duplication between the
on-disk YAML workflow-definition store and the durable `agent_runs` table: `lastExecutionStatus`
and `lastExecutionAt` are no longer written into the YAML definition at all — they are derived at
read time from `agent_runs` via `WorkflowLastExecutionService`, and `WorkflowDefinitionRepository`
strips any such runtime-state pair from every definition it persists (`withLastExecution(null,
null)`, applied through a single `persist(...)` choke point covering both `save` and `ensureId`).

This closes the specific, previously real concurrency risk on the **execution path**:
`WorkflowExecutionService.doStart` used to re-save the whole workflow definition immediately after
starting a run, positionally reconstructing it — the single most likely place a newly added field
(e.g. `approvalGate`) could silently disappear the first time its workflow ran, and a genuine race
window against any concurrent definition write. That re-save is now deleted outright; `doStart` no
longer writes the definition file at all.

**The execution-path race is removed; six other write paths through the same shared store retain
the theoretical race, accepted as an out-of-scope residual risk.** Naming all six, exactly as they
exist in the codebase today:

1. `WorkflowController.importBundle`
2. `WorkflowController.create`
3. `WorkflowController.update`
4. `WorkflowGroupController` group-unlink
5. `DefaultWorkflowImporter` seeding
6. `WorkflowDefinitionRepository.ensureId`

None of these six writers is touched by this delivery. Each still reads-modifies-writes the
on-disk YAML file without an atomic compare-and-swap or file lock, so two callers racing against
the same workflow id could in principle still interleave. This is an explicit, accepted,
out-of-scope residual risk (Q1 of this delivery's requirements), not an oversight — no artifact of
this delivery claims that race is fixed, only that the execution-path instance of it (the one this
delivery actually touches) has been removed by deleting the write it depended on.
