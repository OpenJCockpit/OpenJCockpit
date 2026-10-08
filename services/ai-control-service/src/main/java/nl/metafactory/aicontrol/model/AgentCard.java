package nl.metafactory.aicontrol.model;

public record AgentCard(
        String id,
        String name,
        String description,
        String status,
        String statusKind
) {}
