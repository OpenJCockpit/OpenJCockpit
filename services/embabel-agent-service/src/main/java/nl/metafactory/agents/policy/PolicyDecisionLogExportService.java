package nl.metafactory.agents.policy;

import nl.metafactory.agents.policy.model.DecisionLogFilter;
import nl.metafactory.agents.policy.model.PolicyDecisionAuditEntry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Exports policy decision audit entries for a workflow or a specific workflow execution — the
 * data is structured to be usable directly as input for a future customer-facing evidence PDF
 * (see PolicyDecisionAuditEntry javadoc for what is deliberately excluded).
 */
@Service
public class PolicyDecisionLogExportService {

    private final PolicyDecisionAuditRepository repository;

    public PolicyDecisionLogExportService(PolicyDecisionAuditRepository repository) {
        this.repository = repository;
    }

    public List<PolicyDecisionAuditEntry> exportDecisionLogsForWorkflow(String workflowId, DecisionLogFilter filter) {
        return repository.findAll().stream()
                .filter(entry -> Objects.equals(workflowId, entry.workflowId()))
                .filter(entry -> matches(entry, filter))
                .toList();
    }

    public List<PolicyDecisionAuditEntry> exportDecisionLogsForWorkflowExecution(String workflowExecutionId, DecisionLogFilter filter) {
        return repository.findAll().stream()
                .filter(entry -> Objects.equals(workflowExecutionId, entry.workflowExecutionId()))
                .filter(entry -> matches(entry, filter))
                .toList();
    }

    private boolean matches(PolicyDecisionAuditEntry entry, DecisionLogFilter filter) {
        if (filter == null) {
            return true;
        }
        if (filter.from() != null && entry.timestamp().isBefore(filter.from())) {
            return false;
        }
        if (filter.to() != null && entry.timestamp().isAfter(filter.to())) {
            return false;
        }
        if (filter.agentId() != null && !filter.agentId().equals(entry.agentId())) {
            return false;
        }
        if (filter.subagentId() != null && !filter.subagentId().equals(entry.subagentId())) {
            return false;
        }
        if (filter.skillId() != null && !filter.skillId().equals(entry.skillId())) {
            return false;
        }
        if (filter.mcpToolName() != null && !filter.mcpToolName().equals(entry.mcpToolName())) {
            return false;
        }
        if (filter.decisionResult() == null) {
            return true;
        }
        if ("REQUIRES_APPROVAL".equalsIgnoreCase(filter.decisionResult())) {
            return entry.requiredApproval();
        }
        return filter.decisionResult().equalsIgnoreCase(entry.opaDecisionResult());
    }
}
