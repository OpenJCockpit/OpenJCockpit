package nl.metafactory.agents.policy.model;

/**
 * Read-facing view of OpaProperties — authToken is never returned raw, only whether one is set.
 */
public record OpaConfigDto(
        boolean enabled,
        String baseUrl,
        String policyPath,
        String healthPath,
        int timeoutSeconds,
        String failMode,
        boolean decisionLoggingEnabled,
        boolean decisionLogExportEnabled,
        String environment,
        String customerLabel,
        String projectLabel,
        boolean hasAuthToken
) {
}
