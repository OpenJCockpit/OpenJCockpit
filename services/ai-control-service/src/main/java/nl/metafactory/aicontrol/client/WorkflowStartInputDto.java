package nl.metafactory.aicontrol.client;

public record WorkflowStartInputDto(
        String prompt,
        String specFile,
        String repositoryUrl,
        String projectId,
        String gitUsername,
        String gitToken,
        String baseBranch,
        // Single-use ticket (issued by embabel to ai-control's service identity) that binds this start to the
        // git workspace ai-control prepared. Set only by ai-control itself; a caller-supplied value is
        // dropped (WorkflowStartInputs). A secret: never logged.
        String gitWorkspaceTicket
) {
    public WorkflowStartInputDto(String prompt, String specFile, String repositoryUrl, String projectId,
                                 String gitUsername, String gitToken, String baseBranch) {
        this(prompt, specFile, repositoryUrl, projectId, gitUsername, gitToken, baseBranch, null);
    }

    public WorkflowStartInputDto withGitWorkspaceTicket(String ticket) {
        return new WorkflowStartInputDto(prompt, specFile, repositoryUrl, projectId, gitUsername, gitToken, baseBranch,
                ticket);
    }

    @Override
    public String toString() {
        return "WorkflowStartInputDto[projectId=" + projectId + ", baseBranch=" + baseBranch + ", gitToken=***, "
                + "gitWorkspaceTicket=" + (gitWorkspaceTicket == null ? "null" : "***") + "]";
    }

    public static WorkflowStartInputDto empty() {
        return new WorkflowStartInputDto(null, null, null, null, null, null, null, null);
    }
}
