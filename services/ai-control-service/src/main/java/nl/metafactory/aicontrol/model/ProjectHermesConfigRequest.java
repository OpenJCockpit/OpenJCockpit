package nl.metafactory.aicontrol.model;

public record ProjectHermesConfigRequest(
        boolean enabled,
        String endpointUrl,
        String authToken,
        String signalType,
        String workflowId
) {}
