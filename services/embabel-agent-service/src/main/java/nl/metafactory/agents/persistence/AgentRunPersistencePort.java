package nl.metafactory.agents.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The persistence seam a later package will use to make workflow-execution history durable.
 *
 * <p>Failure policy by design:
 * <ul>
 *   <li>{@link #recordNew} is fail-closed — it either commits or throws; the caller must treat
 *       any exception as "this run was not durably recorded, do not proceed".</li>
 *   <li>{@link #appendEvent} and {@link #appendArtifact} are best-effort by contract — they may
 *       throw, and it is the CALLER's responsibility to catch/log and continue. This
 *       implementation of this interface must never swallow those exceptions itself.</li>
 *   <li>{@link #flushTerminal} is one transactional operation, safe to call more than once
 *       (idempotent): it writes the run's final status/completedAt and inserts only the
 *       events/artifacts not already persisted, by sequence number.</li>
 *   <li>{@link #listByWorkflow} never fabricates rows; {@link #findFullRun} returns
 *       {@link Optional#empty()} for an unknown run id and never synthesizes a run.</li>
 * </ul>
 */
public interface AgentRunPersistencePort {

    /**
     * Durably records a brand-new run before (or atomically with) starting its pipeline.
     * Fail-closed: either the row is committed, or this method throws.
     */
    void recordNew(String runId, String workflowId, String customerId, String specFile,
                   String repositoryUrl, String status, Instant startedAt, String startedBy);

    /**
     * Best-effort append of one event. Does not catch/swallow exceptions; propagates them to
     * the caller, which decides the failure policy (e.g. log-and-continue).
     */
    void appendEvent(String runId, int sequenceNo, Instant occurredAt, String agentId,
                      String title, String status, String evidenceRef);

    /**
     * Best-effort append of one artifact. Does not catch/swallow exceptions; propagates them to
     * the caller.
     */
    void appendArtifact(String runId, int sequenceNo, String artifact);

    /**
     * One transactional flush to a run's terminal state: writes the final status and
     * completedAt, and inserts only the events/artifacts whose sequence number is greater than
     * whatever is already persisted for this run id (idempotent, safe to call more than once,
     * including after earlier best-effort appends of some but not all events/artifacts).
     *
     * <p>{@code completedAt} may be {@code null} — never fabricate a completion time for a run
     * whose real end time is unknown (e.g. a reconciled/interrupted run).
     *
     * <p>{@code failureSummary} may be null and must be null for every status other than FAILED;
     * a non-null value is written once and is never overwritten by a subsequent null, preserving
     * idempotence.
     */
    void flushTerminal(String runId, String finalStatus, Instant completedAt, String failureSummary,
                        List<EventSnapshot> events, List<String> artifacts);

    /**
     * Returns a summary page for the given workflow id: rows ordered by startedAt desc, runId
     * desc, sliced by limit/offset, plus the total row count and a computed hasMore flag.
     */
    PersistedRunSummaryPage listByWorkflow(String workflowId, int limit, int offset);

    /**
     * Rehydrates one full run (all fields, all events ordered, all artifacts ordered) by id.
     * Returns {@link Optional#empty()} for an unknown id — never synthesizes a run.
     */
    Optional<FullRun> findFullRun(String runId);

    /**
     * Derives the single most recent run per workflow id, for the given collection of workflow
     * ids. Workflow ids with no runs are simply absent from the returned map — never fabricated
     * as a synthetic "never run" entry.
     */
    Map<String, LastExecution> findLatestRunPerWorkflow(Collection<String> workflowIds);

    /**
     * An immutable snapshot of one event, used by {@link #flushTerminal} to describe the
     * in-memory caller's current event list.
     */
    record EventSnapshot(int sequenceNo, Instant occurredAt, String agentId, String title,
                          String status, String evidenceRef) {
    }

    /**
     * One row of a summary page — deliberately excludes events/artifacts (the summary/detail
     * split is enforced at the query layer, not just the DTO layer).
     */
    record PersistedRunSummary(String runId, String workflowId, String status, Instant startedAt,
                                Instant completedAt, String startedBy) {
    }

    /**
     * A page of {@link PersistedRunSummary} rows for one workflow id.
     */
    record PersistedRunSummaryPage(List<PersistedRunSummary> items, long total, boolean hasMore) {
    }

    /**
     * A fully rehydrated run: every column of the parent row plus its ordered events and
     * artifacts.
     */
    record FullRun(String runId, String workflowId, String customerId, String specFile,
                    String repositoryUrl, String status, Instant startedAt, Instant completedAt,
                    String startedBy, Instant reconciledAt, List<EventSnapshot> events,
                    List<String> artifacts, String failureSummary) {
    }

    /**
     * The single most recent run of one workflow id, derived by {@link #findLatestRunPerWorkflow}.
     */
    record LastExecution(String workflowId, String status, Instant startedAt) {
    }
}
