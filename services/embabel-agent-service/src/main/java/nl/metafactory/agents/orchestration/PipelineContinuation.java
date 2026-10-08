package nl.metafactory.agents.orchestration;

/**
 * Breaks the bean cycle between the approval-decision layer (added in batch B7) and
 * {@code nl.metafactory.agents.workflowtrigger} layer on one side, and {@link EmbabelOrchestrator}
 * on the other: {@code ApprovalDecisionService} and {@code WorkflowTriggerCoordinator} both depend
 * on this interface rather than on the orchestrator concretely, so the orchestrator can in turn
 * depend on their collaborators (e.g. the approval-gate registry, the child-wait registry) without
 * a circular constructor dependency. Implemented by {@link EmbabelOrchestrator}. Both callers honour
 * the same evict-before-resume discipline against their respective registries, so the single-owner
 * invariant on {@link PipelineState} holds identically for either caller.
 */
public interface PipelineContinuation {

    /** Resumes a parked run from wherever {@code state} left off. */
    void resume(PipelineState state);
}
