package nl.metafactory.common.agentruntime;

import java.util.Map;

/**
 * The agent service's policy decision (OPA {@code mcp.tool.invoke}, audited in the agent service's decision
 * log) on one tool call of a running agent.
 */
public record ToolInvocationDecision(boolean allowed, String reason) {

    /**
     * Identifiers of the tool call; never tool input or output.
     *
     * @param workflowId    the workflow of the run, null outside a workflow (the policy then refuses)
     * @param workflowRunId the workflow run, or the agent run id outside a workflow
     * @param attributes    correlation only (trace id, agent version, agent run id)
     */
    public record Request(String workflowId, String workflowRunId, String tenantId, String agentId, String toolId,
                          String principal, Map<String, String> attributes) {

        public Request {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }
}
