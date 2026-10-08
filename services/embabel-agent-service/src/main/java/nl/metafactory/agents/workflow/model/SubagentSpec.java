package nl.metafactory.agents.workflow.model;

import java.util.List;

public record SubagentSpec(
        String name,
        String parentAgent,
        String description,
        String responsibilities,
        String instructions,
        List<String> skillNames,
        List<McpToolRef> mcpTools,
        String workflowId
) {
}
