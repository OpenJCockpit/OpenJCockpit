package nl.metafactory.aicontrol.client;

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
