package nl.metafactory.agents.api;

import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.workflow.WorkflowExecutionHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only, workflow-scoped execution history. Distinct from the existing, unscoped
 * {@code /api/agent-runs/{runId}} (legacy, stays as-is by product decision) and from
 * {@code WorkflowExecutionController} (decision-logs, {@code /api/workflow-executions}, a different
 * class on a different path root). All scoping/validation logic lives in
 * {@link WorkflowExecutionHistoryService} — this controller only delegates.
 */
@RestController
@RequestMapping("/api/workflows/{workflowId}/executions")
public class WorkflowExecutionHistoryController {

    private final WorkflowExecutionHistoryService historyService;

    public WorkflowExecutionHistoryController(WorkflowExecutionHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public WorkflowExecutionHistoryService.WorkflowExecutionHistoryPage list(
            @PathVariable String workflowId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return historyService.list(workflowId, limit, offset);
    }

    @GetMapping("/{runId}")
    public AgentRun detail(@PathVariable String workflowId, @PathVariable String runId) {
        return historyService.detail(workflowId, runId);
    }
}