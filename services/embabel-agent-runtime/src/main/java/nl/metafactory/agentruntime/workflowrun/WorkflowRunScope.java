package nl.metafactory.agentruntime.workflowrun;

import nl.metafactory.agentruntime.gitworkspace.GitWorkspaceBinding;
import nl.metafactory.common.agentruntime.WorkflowRunDescriptor;

import java.util.Objects;

/**
 * The workflow run an agent run belongs to: which workflow, which run and (for runs on a project) which
 * git workspace its git tools may use. Resolved from the agent service (the workflow engine's own registry),
 * never from a caller, an agent or an LLM; it attributes the agent's tool calls to the policy decision
 * point (OPA). Identifiers only — safe to log.
 */
public record WorkflowRunScope(String workflowId, String workflowRunId, GitWorkspaceBinding gitWorkspace) {

    public WorkflowRunScope {
        Objects.requireNonNull(workflowRunId, "workflowRunId");
    }

    /** Validates the agent service's descriptor; invalid identifiers are rejected, never passed on. */
    public static WorkflowRunScope from(WorkflowRunDescriptor descriptor) {
        var ws = descriptor.gitWorkspace();
        return new WorkflowRunScope(descriptor.workflowId(), descriptor.workflowRunId(), ws == null ? null
                : new GitWorkspaceBinding(ws.workspaceKey(), ws.repositoryUrl(), ws.branch()));
    }
}
