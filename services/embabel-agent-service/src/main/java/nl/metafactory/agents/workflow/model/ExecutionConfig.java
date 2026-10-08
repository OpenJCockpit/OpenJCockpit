package nl.metafactory.agents.workflow.model;

public record ExecutionConfig(
        String customerId,
        String repositoryUrl,
        Integer timeoutSeconds
) {
}
