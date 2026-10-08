package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.model.AgentEvent;
import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.model.AgentRunStatuses;
import nl.metafactory.agents.model.AgentRunSummary;
import nl.metafactory.agents.model.AgentRunSummaryPage;
import nl.metafactory.agents.persistence.AgentRunPersistencePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionException;
import org.springframework.stereotype.Component;
import nl.metafactory.agents.workflowtrigger.RunTerminalEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extracted from {@link EmbabelOrchestrator} (ADR-001, workflow-approval-gate architecture §4.3,
 * batch B4) so the approval-gate coordinator and decision service (added in later batches) can
 * also emit events and flip run status without creating a circular constructor dependency on the
 * orchestrator itself.
 *
 * <p>The in-memory map remains authoritative and zero-latency for a currently-live run (used by
 * {@link #get(String)}, and by the fast path of {@link #find(String)}, {@link #recordEvent}
 * {@link #addArtifact} and {@link #setStatus}), but every run is now also durably persisted via
 * {@link AgentRunPersistencePort}, so {@link #listByWorkflow(String, int, int)} and
 * {@link #find(String)} return durable, restart-surviving results even for runs no longer
 * resident in memory. {@link EmbabelOrchestrator#get(String)} still maps an unknown run id to the
 * explicit terminal {@code RUN_STATE_LOST} status rather than hanging a poller indefinitely (V8);
 * that behaviour is unrelated to durable persistence and remains in effect.
 *
 * <p>Also exposes {@link #listByWorkflow(String, int, int)}, a read-only, workflow-scoped,
 * paginated view used by the workflow-execution-history feature, now backed by durable storage
 * with an in-memory overlay for any run still live in this process.
 */
@Component
public class AgentRunStore {

    private static final Logger LOG = LoggerFactory.getLogger(AgentRunStore.class);

    private final Map<String, AgentRun> runs = new ConcurrentHashMap<>();
    private final Set<String> cancelledRuns = ConcurrentHashMap.newKeySet();
    private final AgentRunPersistencePort persistence;
    private final ApplicationEventPublisher eventPublisher;

    public AgentRunStore(AgentRunPersistencePort persistence, ApplicationEventPublisher eventPublisher) {
        this.persistence = persistence;
        this.eventPublisher = eventPublisher;
    }

    public AgentRun create(String runId, String customerId, String specFile, String repositoryUrl,
                            String workflowId, String startedBy) {
        Instant startedAt = Instant.now();
        try {
            persistence.recordNew(runId, workflowId, customerId, specFile, repositoryUrl, "RUNNING", startedAt, startedBy);
        } catch (DataAccessException | TransactionException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Workflow execution history is currently unavailable");
        }
        var run = new AgentRun(runId, customerId, specFile, repositoryUrl, "RUNNING", startedAt,
                new ArrayList<>(), new ArrayList<>(), workflowId, startedBy, null, null);
        runs.put(runId, run);
        return run;
    }

    /** Null when unknown to this instance — callers decide how to represent that. */
    public AgentRun get(String runId) {
        return runs.get(runId);
    }

    public void recordEvent(String runId, String agentId, String title, String status, String evidenceRef) {
        AgentRun updated = runs.computeIfPresent(runId, (key, run) -> {
            var events = new ArrayList<>(run.events());
            events.add(new AgentEvent(Instant.now(), agentId, title, status, evidenceRef));
            return run.withEvents(events);
        });
        if (updated != null) {
            int sequenceNo = updated.events().size() - 1;
            AgentEvent event = updated.events().get(sequenceNo);
            try {
                persistence.appendEvent(runId, sequenceNo, event.timestamp(), event.agentId(),
                        event.title(), event.status(), event.evidenceRef());
            } catch (Exception e) {
                LOG.warn("Failed to persist event for run {}", runId, e);
            }
        }
    }

    public void addArtifact(String runId, String artifact) {
        AgentRun updated = runs.computeIfPresent(runId, (key, run) -> {
            var artifacts = new ArrayList<>(run.generatedArtifacts());
            artifacts.add(artifact);
            return run.withGeneratedArtifacts(artifacts);
        });
        if (updated != null) {
            int sequenceNo = updated.generatedArtifacts().size() - 1;
            try {
                persistence.appendArtifact(runId, sequenceNo, artifact);
            } catch (Exception e) {
                LOG.warn("Failed to persist artifact for run {}", runId, e);
            }
        }
    }

    /**
     * Sets a run's status with no failure summary. Delegates to {@link #setStatus(String, String, String)}
     * with a null failure summary — the correct call for every terminal status other than FAILED.
     */
    public void setStatus(String runId, String status) {
        setStatus(runId, status, null);
    }

    /**
     * Sets a run's status, optionally recording a sanitised, bounded terminal failure summary.
     * {@code failureSummary} must be null for every status other than FAILED (AC-13).
     */
    public void setStatus(String runId, String status, String failureSummary) {
        AgentRun updated = runs.computeIfPresent(runId, (key, run) -> {
            AgentRun withStatus;
            if (AgentRunStatuses.isTerminal(status) && run.completedAt() == null) {
                withStatus = run.withStatusAndCompletion(status, Instant.now());
            } else {
                withStatus = run.withStatusAndCompletion(status, run.completedAt());
            }
            if (failureSummary != null) {
                return withStatus.withFailureSummary(failureSummary);
            }
            return withStatus;
        });
        if (updated != null && AgentRunStatuses.isTerminal(status)) {
            List<AgentRunPersistencePort.EventSnapshot> eventSnapshots = new ArrayList<>();
            for (int i = 0; i < updated.events().size(); i++) {
                AgentEvent e = updated.events().get(i);
                eventSnapshots.add(new AgentRunPersistencePort.EventSnapshot(
                        i, e.timestamp(), e.agentId(), e.title(), e.status(), e.evidenceRef()));
            }
            try {
                persistence.flushTerminal(runId, updated.status(), updated.completedAt(), updated.failureSummary(),
                        eventSnapshots, updated.generatedArtifacts());
            } catch (Exception e) {
                LOG.error("Failed to flush terminal state for run {}", runId, e);
            }
            eventPublisher.publishEvent(new RunTerminalEvent(runId, status));
            if ("FAILED".equals(status) && updated.failureSummary() != null) {
                LOG.info("run.failure.recorded runId={} status={} summaryLength={}", runId, status, updated.failureSummary().length());
            }
        }
    }

    /**
     * Workflow-scoped, paginated, deterministically ordered view sourced from durable storage
     * (via {@link AgentRunPersistencePort#listByWorkflow}), with an overlay: any row whose runId
     * is still resident in the in-memory map has its {@code status}/{@code completedAt} replaced
     * by the in-memory value, since that is more up to date than a possibly-lagging durable write.
     * {@code limit} is clamped to [1, 100] and {@code offset} to {@code >= 0} defensively, and the
     * returned page echoes the clamped, effective values.
     */
    public AgentRunSummaryPage listByWorkflow(String workflowId, int limit, int offset) {
        int effectiveLimit = Math.max(1, Math.min(100, limit));
        int effectiveOffset = Math.max(0, offset);

        AgentRunPersistencePort.PersistedRunSummaryPage basePage =
                persistence.listByWorkflow(workflowId, effectiveLimit, effectiveOffset);

        List<AgentRunSummary> mappedItems = new ArrayList<>();
        for (AgentRunPersistencePort.PersistedRunSummary s : basePage.items()) {
            AgentRun inMemory = runs.get(s.runId());
            String effectiveStatus = inMemory != null ? inMemory.status() : s.status();
            Instant effectiveCompletedAt = inMemory != null ? inMemory.completedAt() : s.completedAt();
            mappedItems.add(new AgentRunSummary(s.runId(), s.workflowId(), effectiveStatus,
                    s.startedAt(), effectiveCompletedAt, s.startedBy()));
        }

        return new AgentRunSummaryPage(mappedItems, effectiveLimit, effectiveOffset, basePage.total(), basePage.hasMore());
    }

    /**
     * Memory-first, durable-fallback lookup of one full run: checks the in-memory map first
     * (zero latency/behavior change on the live path), and only if absent there, rehydrates from
     * durable storage via {@link AgentRunPersistencePort#findFullRun}. Returns {@link Optional#empty()}
     * for a truly unknown id — never fabricates a run.
     */
    public Optional<AgentRun> find(String runId) {
        AgentRun inMemory = runs.get(runId);
        if (inMemory != null) {
            return Optional.of(inMemory);
        }
        return persistence.findFullRun(runId).map(full -> {
            List<AgentEvent> mappedEvents = new ArrayList<>();
            for (AgentRunPersistencePort.EventSnapshot es : full.events()) {
                mappedEvents.add(new AgentEvent(es.occurredAt(), es.agentId(), es.title(), es.status(), es.evidenceRef()));
            }
            return new AgentRun(full.runId(), full.customerId(), full.specFile(), full.repositoryUrl(),
                    full.status(), full.startedAt(), mappedEvents, full.artifacts(), full.workflowId(),
                    full.startedBy(), full.completedAt(), full.failureSummary());
        });
    }

    public void markCancelled(String runId) {
        cancelledRuns.add(runId);
    }

    public boolean isCancelled(String runId) {
        return cancelledRuns.contains(runId);
    }
}
