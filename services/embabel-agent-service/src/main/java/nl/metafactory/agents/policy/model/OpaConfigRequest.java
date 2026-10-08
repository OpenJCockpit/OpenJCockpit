package nl.metafactory.agents.policy.model;

/**
 * All fields except "enabled" are nullable — null means "leave this field unchanged." authToken
 * follows the same convention as the project git-credential update pattern: null keeps the
 * existing token, an empty string clears it, anything else replaces it.
 */
public record OpaConfigRequest(
        boolean enabled,
        String baseUrl,
        String policyPath,
        String healthPath,
        Integer timeoutSeconds,
        String failMode,
        Boolean decisionLoggingEnabled,
        Boolean decisionLogExportEnabled,
        String environment,
        String customerLabel,
        String projectLabel,
        String authToken
) {
}
