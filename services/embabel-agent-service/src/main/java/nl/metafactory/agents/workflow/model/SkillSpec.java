package nl.metafactory.agents.workflow.model;

import java.util.List;

public record SkillSpec(
        String name,
        String description,
        String inputContract,
        String outputContract,
        String executionInstructions,
        List<McpToolRef> mcpTools,
        String policyNotes
) {
}
