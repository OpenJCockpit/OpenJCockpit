package nl.metafactory.agents.approval.model;

/**
 * Persisted approval-gate configuration on a {@link nl.metafactory.agents.workflow.model.WorkflowDefinition}.
 * Off by default (BR-02): a workflow with no gate configuration behaves exactly as before this
 * feature. {@code placementStage} must be one of the ids actually selected in that workflow's
 * {@code agentIds} (BR-03/BR-04) — validated at the API boundary in {@code WorkflowController},
 * never here. Placement is free for any selected stage; only the loop-back *capability* is
 * conditional on the placement being the realisation stage, computed once in
 * {@code ApprovalGateCoordinator} (BR-41) — this record does not encode that distinction.
 */
public record ApprovalGateConfig(boolean enabled, String placementStage) {
}
