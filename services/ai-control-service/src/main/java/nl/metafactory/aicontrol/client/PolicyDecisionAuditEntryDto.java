package nl.metafactory.aicontrol.client;

import java.time.Instant;
import java.util.List;

public record PolicyDecisionAuditEntryDto(
        String id,
        String workflowId,
        String workflowExecutionId,
        String projectId,
        String projectName,
        String customerId,
        String customerName,
        String agentId,
        String subagentId,
        String skillId,
        String mcpToolName,
        String triggerSource,
        String requestedAction,
        String opaDecisionId,
        String opaDecisionResult,
        String policyReason,
        String riskLevel,
        boolean requiredApproval,
        List<String> auditTags,
        Instant timestamp,
        String policyRevision,
        boolean policyUnavailable,
        String failModeApplied
) {
}
