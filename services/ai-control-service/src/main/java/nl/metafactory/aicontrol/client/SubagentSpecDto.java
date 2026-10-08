package nl.metafactory.aicontrol.client;

import java.util.List;

public record SubagentSpecDto(
        String name,
        String parentAgent,
        String description,
        String responsibilities,
        String instructions,
        List<String> skillNames,
        List<McpToolRefDto> mcpTools,
        String workflowId
) {
}
