package nl.metafactory.aicontrol.model;

import java.util.UUID;

public record ProjectHermesConfigDto(
        UUID projectId,
        boolean enabled,
        String endpointUrl,
        String signalType,
        String workflowId,
        boolean hasAuthToken
) {}
