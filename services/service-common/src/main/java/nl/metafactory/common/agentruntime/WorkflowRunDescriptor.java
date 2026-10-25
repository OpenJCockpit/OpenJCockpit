package nl.metafactory.common.agentruntime;

/**
 * An active workflow run as the agent service reports it to the runtime: which workflow, which run and —
 * for runs on a project — which git workspace its agents' git tools may use. Identifiers only.
 */
public record WorkflowRunDescriptor(String workflowId, String workflowRunId, GitWorkspace gitWorkspace) {

    /** A workspace of the git MCP server: the clone of {@code repositoryUrl} with {@code branch} checked out. */
    public record GitWorkspace(String workspaceKey, String repositoryUrl, String branch) {
    }
}
