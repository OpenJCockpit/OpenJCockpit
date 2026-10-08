package nl.metafactory.aicontrol.client;

import java.util.List;

public record SkillSpecDto(
        String name,
        String description,
        String inputContract,
        String outputContract,
        String executionInstructions,
        List<McpToolRefDto> mcpTools,
        String policyNotes
) {
}
