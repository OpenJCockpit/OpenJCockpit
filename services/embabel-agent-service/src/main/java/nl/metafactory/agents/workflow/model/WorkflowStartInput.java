package nl.metafactory.agents.workflow.model;

/**
 * Optional input when starting a workflow from the dashboard:
 * a prompt (for workflows with {@code promptRequired}, such as spec creation
 * from prompt + repository contents), the selected spec file and/or the
 * git URL of the selected project — the latter determines where the
 * SpecGitPublisher pushes the spec branch.
 */
public record WorkflowStartInput(
        String prompt,
        String specFile,
        String repositoryUrl,
        String gitUsername,
        String gitToken,
        // MADP-54: server-populated base branch (the project's defaultBranch, resolved in
        // ai-control's WorkflowStartEnrichmentService). Absent/blank → the agent service falls
        // back to metafactory.spec-git.base-branch. Only the dashboard-button start path carries
        // it; Hermes-signal and project-folder-file starts pass WorkflowStartInput.empty().
        String baseBranch
) {
    public static WorkflowStartInput empty() {
        return new WorkflowStartInput(null, null, null, null, null, null);
    }
}
