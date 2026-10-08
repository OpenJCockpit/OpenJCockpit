package nl.metafactory.aicontrol.client;

import java.util.List;

public record AgentSpecDto(
        String name,
        String description,
        String role,
        String instructions,
        List<String> subagentNames,
        List<String> skillNames,
        List<McpToolRefDto> mcpTools,
        String workflowId,
        boolean builtIn
) {
}
