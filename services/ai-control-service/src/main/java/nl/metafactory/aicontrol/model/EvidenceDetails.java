package nl.metafactory.aicontrol.model;

public record EvidenceDetails(
        String specVersion,
        String runId,
        String agentFlow,
        String lastUpdate
) {}
