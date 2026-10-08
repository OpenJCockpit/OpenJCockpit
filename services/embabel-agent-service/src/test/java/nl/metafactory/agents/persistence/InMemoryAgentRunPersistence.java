package nl.metafactory.agents.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A hand-written, genuinely-working in-memory fake for {@link AgentRunPersistencePort}.
 *
 * <p>This is NOT a Mockito mock and NOT a stub: it maintains real in-memory state
 * (a {@link ConcurrentHashMap} of run states) and implements the full create / append /
 * flush / list / rehydrate contract so that test packages can substitute it for the
 * real JPA-backed implementation without a database.
 *
 * <p>Known intentional differences from {@code JpaAgentRunPersistence}:
 * <ul>
 *   <li>{@code recordNew} does NOT mask the {@code repositoryUrl}; the real
 *       implementation masks it before persisting. This fake stores it verbatim.
 *   <li>{@code flushTerminal} simply REPLACES the stored events/artifacts lists with
 *       copies of the given lists (events sorted by sequenceNo). Unlike the real JPA
 *       implementation, which must avoid re-inserting rows already committed
 *       individually via {@code appendEvent}/{@code appendArtifact}, this in-memory
 *       fake holds everything in one process-local map with no separate insert-time
 *       uniqueness constraint to violate, so replacement is safe and simpler.
 *   <li>{@code appendArtifact} ignores the {@code sequenceNo} parameter for ordering
 *       purposes and simply appends to the list in call order. The fake's contract is
 *       "what you appended shows up in the right run, in a workable order"; it does
 *       not need byte-identical ordering semantics under out-of-order sequence numbers.
 *   <li>{@code flushTerminal}'s {@code failureSummary} parameter is write-once, mirroring
 *       the real implementation: a non-null value is stored, and a subsequent {@code null}
 *       never erases a previously recorded value.
 * </ul>
 */
public class InMemoryAgentRunPersistence implements AgentRunPersistencePort {

    private final Map<String, RunState> runs = new ConcurrentHashMap<>();

    /**
     * Mutable holder for a single run's state. Mutations to the events/artifacts
     * lists are guarded by synchronizing on the RunState instance.
     */
    private static final class RunState {
        final String runId;
        final String workflowId;
        final String customerId;
        final String specFile;
        final String repositoryUrl;
        String status;
        final Instant startedAt;
        Instant completedAt;
        final String startedBy;
        Instant reconciledAt;
        String failureSummary;
        final List<EventSnapshot> events = new ArrayList<>();
        final List<String> artifacts = new ArrayList<>();

        RunState(String runId, String workflowId, String customerId, String specFile,
                 String repositoryUrl, String status, Instant startedAt, String startedBy) {
            this.runId = runId;
            this.workflowId = workflowId;
            this.customerId = customerId;
            this.specFile = specFile;
            this.repositoryUrl = repositoryUrl;
            this.status = status;
            this.startedAt = startedAt;
            this.startedBy = startedBy;
            this.completedAt = null;
            this.reconciledAt = null;
            this.failureSummary = null;
        }
    }

    @Override
    public void recordNew(String runId, String workflowId, String customerId, String specFile,
                           String repositoryUrl, String status, Instant startedAt, String startedBy) {
        RunState existing = runs.putIfAbsent(runId,
                new RunState(runId, workflowId, customerId, specFile, repositoryUrl, status, startedAt, startedBy));
        if (existing != null) {
            throw new IllegalStateException("Run already exists: " + runId);
        }
    }

    @Override
    public void appendEvent(String runId, int sequenceNo, Instant occurredAt, String agentId,
                             String title, String status, String evidenceRef) {
        RunState state = requireRun(runId);
        synchronized (state) {
            state.events.add(new EventSnapshot(sequenceNo, occurredAt, agentId, title, status, evidenceRef));
        }
    }

    @Override
    public void appendArtifact(String runId, int sequenceNo, String artifact) {
        RunState state = requireRun(runId);
        synchronized (state) {
            state.artifacts.add(artifact);
        }
    }

    @Override
    public void flushTerminal(String runId, String finalStatus, Instant completedAt, String failureSummary,
                               List<EventSnapshot> events, List<String> artifacts) {
        RunState state = requireRun(runId);
        synchronized (state) {
            state.status = finalStatus;
            state.completedAt = completedAt;
            if (failureSummary != null) {
                state.failureSummary = failureSummary;
            }
            // Replace events with a copy of the given list, sorted by sequenceNo ascending.
            List<EventSnapshot> sorted = new ArrayList<>(events);
            sorted.sort(Comparator.comparingInt(EventSnapshot::sequenceNo));
            state.events.clear();
            state.events.addAll(sorted);
            // Replace artifacts with a copy of the given list.
            state.artifacts.clear();
            state.artifacts.addAll(artifacts);
        }
    }

    @Override
    public PersistedRunSummaryPage listByWorkflow(String workflowId, int limit, int offset) {
        List<RunState> filtered = new ArrayList<>();
        for (RunState state : runs.values()) {
            if (Objects.equals(state.workflowId, workflowId)) {
                filtered.add(state);
            }
        }
        // Sort by startedAt descending, then runId descending for ties.
        filtered.sort(Comparator
                .comparing((RunState s) -> s.startedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing((RunState s) -> s.runId, Comparator.reverseOrder()));

        long total = filtered.size();

        int fromIndex = Math.max(0, Math.min(offset, filtered.size()));
        int toIndex = Math.max(fromIndex, Math.min(offset + limit, filtered.size()));

        List<PersistedRunSummary> items = new ArrayList<>();
        for (int i = fromIndex; i < toIndex; i++) {
            RunState state = filtered.get(i);
            items.add(new PersistedRunSummary(
                    state.runId,
                    state.workflowId,
                    state.status,
                    state.startedAt,
                    state.completedAt,
                    state.startedBy));
        }

        boolean hasMore = (offset + items.size()) < total;
        return new PersistedRunSummaryPage(items, total, hasMore);
    }

    @Override
    public Optional<FullRun> findFullRun(String runId) {
        RunState state = runs.get(runId);
        if (state == null) {
            return Optional.empty();
        }
        synchronized (state) {
            List<EventSnapshot> eventsCopy = new ArrayList<>(state.events);
            eventsCopy.sort(Comparator.comparingInt(EventSnapshot::sequenceNo));
            List<String> artifactsCopy = new ArrayList<>(state.artifacts);
            return Optional.of(new FullRun(
                    state.runId,
                    state.workflowId,
                    state.customerId,
                    state.specFile,
                    state.repositoryUrl,
                    state.status,
                    state.startedAt,
                    state.completedAt,
                    state.startedBy,
                    state.reconciledAt,
                    eventsCopy,
                    artifactsCopy,
                    state.failureSummary));
        }
    }

    @Override
    public Map<String, LastExecution> findLatestRunPerWorkflow(Collection<String> workflowIds) {
        if (workflowIds == null || workflowIds.isEmpty()) {
            return Map.of();
        }
        Map<String, RunState> latestByWorkflow = new HashMap<>();
        for (RunState state : runs.values()) {
            if (!workflowIds.contains(state.workflowId)) {
                continue;
            }
            RunState currentLatest = latestByWorkflow.get(state.workflowId);
            if (currentLatest == null || isLater(state, currentLatest)) {
                latestByWorkflow.put(state.workflowId, state);
            }
        }
        Map<String, LastExecution> result = new HashMap<>();
        for (Map.Entry<String, RunState> entry : latestByWorkflow.entrySet()) {
            RunState state = entry.getValue();
            result.put(entry.getKey(), new LastExecution(state.workflowId, state.status, state.startedAt));
        }
        return result;
    }

    /**
     * Tie-break semantics mirroring the production JPQL: greatest {@code startedAt} wins; a tie
     * on {@code startedAt} is broken by the greatest {@code runId} (lexicographically).
     */
    private static boolean isLater(RunState candidate, RunState currentLatest) {
        int startedAtComparison = candidate.startedAt.compareTo(currentLatest.startedAt);
        if (startedAtComparison != 0) {
            return startedAtComparison > 0;
        }
        return candidate.runId.compareTo(currentLatest.runId) > 0;
    }

    private RunState requireRun(String runId) {
        RunState state = runs.get(runId);
        if (state == null) {
            throw new IllegalStateException("Unknown run: " + runId);
        }
        return state;
    }
}
