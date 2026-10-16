package nl.metafactory.agents.workflow.agents;

import java.util.List;

/**
 * The linking table between workflows and the agents they reference ({@code workflow_agent_refs}),
 * in workflow order. The single source of truth for a workflow's agents: workflow YAML files no
 * longer carry them.
 */
public interface WorkflowAgentLinks {

    /** The workflow's agent reference keys in execution order; empty when none are linked. */
    List<String> find(String workflowId);

    /** Replaces the workflow's links with {@code keys} (each a valid {@link AgentRef} key), in order. */
    void replace(String workflowId, List<String> keys);

    void delete(String workflowId);
}
