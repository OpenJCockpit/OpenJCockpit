package nl.metafactory.aicontrol.model;

import java.util.UUID;

public record ProjectJiraConfigDto(
        UUID projectId,
        boolean enabled,
        String baseUrl,
        String projectKey,
        String issueTypeMapping,
        String workflowId,
        boolean hasAuthToken
) {}
