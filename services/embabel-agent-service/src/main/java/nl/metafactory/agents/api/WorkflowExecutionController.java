package nl.metafactory.agents.api;

import nl.metafactory.agents.policy.PolicyDecisionLogExportService;
import nl.metafactory.agents.policy.model.DecisionLogFilter;
import nl.metafactory.agents.policy.model.PolicyDecisionAuditEntry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/workflow-executions")
public class WorkflowExecutionController {

    private final PolicyDecisionLogExportService decisionLogExportService;

    public WorkflowExecutionController(PolicyDecisionLogExportService decisionLogExportService) {
        this.decisionLogExportService = decisionLogExportService;
    }

    @GetMapping("/{id}/decision-logs")
    public List<PolicyDecisionAuditEntry> decisionLogs(
            @PathVariable String id,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String subagentId,
            @RequestParam(required = false) String skillId,
            @RequestParam(required = false) String mcpToolName,
            @RequestParam(required = false) String result) {
        var filter = new DecisionLogFilter(from, to, agentId, subagentId, skillId, mcpToolName, result);
        return decisionLogExportService.exportDecisionLogsForWorkflowExecution(id, filter);
    }
}
