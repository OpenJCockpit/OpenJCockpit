package nl.metafactory.agents.workflowrun;

import nl.metafactory.agents.gitworkspace.GitWorkspaceBinding;

import java.util.Objects;

/**
 * The workflow run an agent run belongs to: which workflow, which run and (for runs on a project) which
 * git workspace its git tools may use. Set by the engine, never by an agent or an LLM; it attributes the
 * agent's tool calls to the policy decision point (OPA). Identifiers only — safe to log.
 */
public record WorkflowRunScope(String workflowId, String workflowRunId, GitWorkspaceBinding gitWorkspace) {

    public WorkflowRunScope {
        Objects.requireNonNull(workflowRunId, "workflowRunId");
    }
}
