package nl.metafactory.agents.api;

import nl.metafactory.agents.policy.PolicyDecisionLogExportService;
import nl.metafactory.agents.policy.model.PolicyDecisionAuditEntry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workflow-executions")
public class WorkflowExecutionController {

    private final PolicyDecisionLogExportService decisionLogExportService;

    public WorkflowExecutionController(PolicyDecisionLogExportService decisionLogExportService) {
        this.decisionLogExportService = decisionLogExportService;
    }

    @GetMapping("/{id}/decision-logs")
    public List<PolicyDecisionAuditEntry> decisionLogs(@PathVariable String id, DecisionLogQuery query) {
        return decisionLogExportService.exportDecisionLogsForWorkflowExecution(id, query.toFilter());
    }
}
