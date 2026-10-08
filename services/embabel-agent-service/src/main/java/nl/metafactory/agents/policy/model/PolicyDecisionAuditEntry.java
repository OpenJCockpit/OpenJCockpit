package nl.metafactory.agents.policy.model;

import java.time.Instant;
import java.util.List;

/**
 * The persisted, correlatable record of a single policy decision. This is the backend's own audit
 * trail — it does not rely on OPA's remote decision logs — and is what decision-log export/evidence
 * generation reads from. Deliberately excludes raw request/response payloads, secrets, tokens, and
 * document contents; only identifiers, labels, classifications, and the decision outcome are kept.
 */
public record PolicyDecisionAuditEntry(
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
