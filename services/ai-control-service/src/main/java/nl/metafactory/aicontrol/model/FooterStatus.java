package nl.metafactory.aicontrol.model;

public record FooterStatus(
        String environment,
        String region,
        String dataClassification,
        String embabelVersion,
        String springAiControlVersion
) {}
