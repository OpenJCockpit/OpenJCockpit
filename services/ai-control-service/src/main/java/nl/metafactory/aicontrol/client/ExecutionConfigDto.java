package nl.metafactory.aicontrol.client;

public record ExecutionConfigDto(
        String customerId,
        String repositoryUrl,
        Integer timeoutSeconds
) {
}
