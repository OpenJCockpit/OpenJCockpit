package nl.metafactory.agents.workflow.model;

import java.util.List;

public record AgentSpec(
        String name,
        String description,
        String role,
        String instructions,
        List<String> subagentNames,
        List<String> skillNames,
        List<McpToolRef> mcpTools,
        String workflowId,
        boolean builtIn
) {
}
