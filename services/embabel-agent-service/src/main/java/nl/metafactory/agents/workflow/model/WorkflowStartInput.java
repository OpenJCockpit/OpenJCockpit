package nl.metafactory.agents.workflow.model;

/**
 * Optional input when starting a workflow from the dashboard:
 * a prompt (for workflows with {@code promptRequired}, such as spec creation
 * from prompt + repository contents), the selected spec file and/or the
 * git URL of the selected project. The git credential fields are passed
 * through to the run request, kept for running the workflow where the
 * repository is cloned.
 */
public record WorkflowStartInput(
        String prompt,
        String specFile,
        String repositoryUrl,
        String gitUsername,
        String gitToken,
        // MADP-54: server-populated base branch (the project's defaultBranch, resolved in
        // ai-control's WorkflowStartEnrichmentService). Absent/blank → the agent service falls
        // back to openjcockpit.spec-git.base-branch. Only the dashboard-button start path carries
        // it; Hermes-signal and project-folder-file starts pass WorkflowStartInput.empty().
        String baseBranch,
        // Single-use ticket issued by ai-control's service identity (GitWorkspaceTicketController) that
        // binds this start to a prepared git workspace. Redeemed only for the workflow and the caller
        // (subject) it was issued for; any other ticket blocks the start. A secret: never logged.
        String gitWorkspaceTicket
) {
    public WorkflowStartInput(String prompt, String specFile, String repositoryUrl, String gitUsername,
                              String gitToken, String baseBranch) {
        this(prompt, specFile, repositoryUrl, gitUsername, gitToken, baseBranch, null);
    }

    public static WorkflowStartInput empty() {
        return new WorkflowStartInput(null, null, null, null, null, null, null);
    }
}
