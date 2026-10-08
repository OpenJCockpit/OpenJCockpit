package nl.metafactory.aicontrol.model;

public record CustomerSummary(
        String id,
        String name,
        String sector,
        String environment,
        String lastActive,
        String dataClassification,
        String region
) {}
