package nl.metafactory.agents.policy.mcp;

import java.util.Map;

/**
 * Identifies a requested MCP tool call and the workflow/agent/subagent/skill context it came from.
 * The payload is kept opaque (never logged/audited raw) — see PolicyGuardedMcpToolGateway.
 */
public record McpToolInvocation(
        String workflowId,
        String workflowExecutionId,
        String projectId,
        String projectName,
        String customerId,
        String customerName,
        String agentId,
        String subagentId,
        String skillId,
        String toolName,
        String operation,
        Map<String, Object> payload
) {
}
