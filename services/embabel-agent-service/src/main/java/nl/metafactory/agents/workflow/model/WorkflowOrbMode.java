package nl.metafactory.agents.workflow.model;

/**
 * Execution mode of a {@link WorkflowOrb}.
 *
 * <p>{@code SEQUENTIAL} parks the parent run and waits for the referenced child workflow to
 * complete successfully before the parent continues. {@code PARALLEL} starts the referenced
 * child workflow and lets the parent continue immediately, never depending on the child's
 * outcome.
 */
public enum WorkflowOrbMode {
    SEQUENTIAL,
    PARALLEL
}
