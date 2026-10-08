package nl.metafactory.aicontrol.client;

public record AgentDefinitionDto(
        String id,
        String name,
        String description,
        String role,
        int sequenceOrder,
        String inputType,
        String outputType
) {}
