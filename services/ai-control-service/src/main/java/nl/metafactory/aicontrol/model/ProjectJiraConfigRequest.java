package nl.metafactory.aicontrol.model;

public record ProjectJiraConfigRequest(
        boolean enabled,
        String baseUrl,
        String projectKey,
        String authToken,
        String issueTypeMapping,
        String workflowId
) {}
