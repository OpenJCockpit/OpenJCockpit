package nl.metafactory.aicontrol.client;

public record OpaConfigRequestDto(
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
