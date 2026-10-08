package nl.metafactory.agents.policy.model;

import java.time.Instant;
import java.util.Map;

/**
 * Input context sent to OPA for a single policy decision. Deliberately carries only identifiers,
 * labels, and classifications — never secrets, tokens, credentials, full document contents, or
 * unmasked personal/customer data (see additionalAttributes javadoc).
 */
public record PolicyDecisionContext(
        String workflowId,
        String workflowExecutionId,
        String projectId,
        String projectName,
        String customerId,
        String customerName,
        String environment,
        String triggerSource,
        String agentId,
        String agentName,
        String subagentId,
        String subagentName,
        String skillId,
        String skillName,
        String mcpToolName,
        String mcpToolOperation,
        String action,
        String userId,
        String userRole,
        String projectClassification,
        Boolean humanApproval,
        String inputDocumentType,
        String filePath,
        String fileEventType,
        Instant timestamp,
        Map<String, Object> additionalAttributes
) {
}
