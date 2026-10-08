package nl.metafactory.agents.workflow.model;

/**
 * A reference from a workflow definition to another workflow definition, executed as part of
 * the parent run in the given {@link WorkflowOrbMode}, optionally anchored to run after a
 * specific stage in the parent's own {@code agentIds} (see {@code placementStage}; absent or
 * blank means the orb runs first, before any of the parent's own agent stages).
 *
 * @param workflowId the id of the referenced workflow definition
 * @param mode the execution mode (SEQUENTIAL or PARALLEL)
 * @param placementStage the id of the parent's own agent stage after which this orb runs, or
 *                        {@code null}/blank to run first
 */
public record WorkflowOrb(String workflowId, WorkflowOrbMode mode, String placementStage) {
}
