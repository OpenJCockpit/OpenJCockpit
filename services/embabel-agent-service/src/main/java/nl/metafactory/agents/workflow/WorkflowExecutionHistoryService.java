package nl.metafactory.agents.workflow;

import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.model.AgentRunSummary;
import nl.metafactory.agents.model.AgentRunSummaryPage;
import nl.metafactory.agents.orchestration.AgentRunStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The single, trusted enforcement point for workflow-scoped execution history (ADR-c,
 * workflow-execution-history architecture). {@code ai-control-service} performs zero authorization
 * or filtering logic of its own — the browser's {@code workflow.id} is a navigation input, never a
 * proof of ownership; this class is where scoping is actually enforced, server-side.
 *
 * <p>{@link #detail(String, String)} deliberately never calls {@code EmbabelOrchestrator.get}: that
 * method fabricates a synthetic {@code RUN_STATE_LOST} run (with invented customerId/specFile and a
 * fresh startedAt) for any unknown run id, which would both violate "never fabricate data" and let
 * a caller distinguish "unknown id" from "belongs to another workflow" by comparing response
 * shapes. Instead this class reads {@link AgentRunStore#find(String)} directly and returns one
 * byte-identical 404 for every one of: unknown run id, a run whose workflowId is null, and a run
 * that belongs to a different workflow.
 */
@Service
public class WorkflowExecutionHistoryService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowExecutionHistoryService.class);

    private final WorkflowDefinitionRepository workflowRepository;
    private final AgentRunStore runStore;

    public WorkflowExecutionHistoryService(WorkflowDefinitionRepository workflowRepository, AgentRunStore runStore) {
        this.workflowRepository = workflowRepository;
        this.runStore = runStore;
    }

    public WorkflowExecutionHistoryPage list(String workflowId, Integer limit, Integer offset) {
        requireWorkflow(workflowId);

        int effectiveLimit = limit == null ? 20 : Math.max(1, Math.min(100, limit));
        int effectiveOffset = offset == null ? 0 : Math.max(0, offset);

        AgentRunSummaryPage page = runStore.listByWorkflow(workflowId, effectiveLimit, effectiveOffset);

        List<WorkflowExecutionSummary> items = page.items().stream()
                .map(WorkflowExecutionHistoryService::toSummary)
                .toList();

        return new WorkflowExecutionHistoryPage(items, page.limit(), page.offset(), page.total(), page.hasMore());
    }

    public AgentRun detail(String workflowId, String runId) {
        requireWorkflow(workflowId);

        Optional<AgentRun> found = runStore.find(runId);
        if (found.isEmpty() || found.get().workflowId() == null || !workflowId.equals(found.get().workflowId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution not found");
        }
        return found.get();
    }

    private void requireWorkflow(String workflowId) {
        try {
            workflowRepository.findById(workflowId).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found: " + workflowId));
        } catch (DefinitionFileReadException e) {
            LOG.debug("workflow.scope.definition-unreadable workflowId={} file={}", workflowId, e.relativeName());
        }
    }

    private static WorkflowExecutionSummary toSummary(AgentRunSummary run) {
        Long durationMillis = run.completedAt() != null
                ? Duration.between(run.startedAt(), run.completedAt()).toMillis()
                : null;
        return new WorkflowExecutionSummary(run.runId(), run.workflowId(), run.status(), run.startedAt(),
                run.completedAt(), durationMillis, run.startedBy());
    }

    /** List-row projection — deliberately excludes {@code events}/{@code generatedArtifacts},
     * which are detail-only and must never be eagerly loaded for a page of history rows. */
    public record WorkflowExecutionSummary(String runId, String workflowId, String status, Instant startedAt,
                                            Instant completedAt, Long durationMillis, String startedBy) {}

    /** Page envelope mirroring {@link AgentRunSummaryPage}, with {@code items} re-mapped to the summary
     * projection above. */
    public record WorkflowExecutionHistoryPage(List<WorkflowExecutionSummary> items, int limit, int offset,
                                                long total, boolean hasMore) {}
}