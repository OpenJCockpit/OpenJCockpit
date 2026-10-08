package nl.metafactory.agents.workflow;

import nl.metafactory.agents.workflow.model.WorkflowStartResponse;

/**
 * Narrow interface breaking the constructor cycle EmbabelOrchestrator -> WorkflowOrbRunner
 * -> WorkflowExecutionService -> AgentOrchestrator(=EmbabelOrchestrator). Implemented by
 * WorkflowExecutionService and injected elsewhere as ObjectProvider<ChildWorkflowStarter>, resolved
 * lazily at call time so no eager circular dependency is created.
 */
public interface ChildWorkflowStarter {

    /**
     * Starts a child workflow on behalf of a workflow orb, applying the same policy path as every
     * other start.
     *
     * @param command the child start command carrying the re-projected parent context
     * @return the resulting start response, with a null executionId if the start was blocked
     */
    WorkflowStartResponse startWorkflowFromOrb(ChildStartCommand command);
}
