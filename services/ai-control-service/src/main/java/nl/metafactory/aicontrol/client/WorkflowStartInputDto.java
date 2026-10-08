package nl.metafactory.aicontrol.client;

public record WorkflowStartInputDto(
        String prompt,
        String specFile,
        String repositoryUrl,
        String projectId,
        String gitUsername,
        String gitToken,
        String baseBranch
) {
    public static WorkflowStartInputDto empty() {
        return new WorkflowStartInputDto(null, null, null, null, null, null, null);
    }
}
