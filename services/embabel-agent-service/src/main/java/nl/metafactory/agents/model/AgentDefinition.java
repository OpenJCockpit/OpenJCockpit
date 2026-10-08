package nl.metafactory.agents.model;

import java.util.List;

public record AgentDefinition(
        String id,
        String name,
        String description,
        String role,
        List<String> allowedTools,
        List<String> requiredOutputs,
        int sequenceOrder,
        String inputType,
        String outputType
) {}
