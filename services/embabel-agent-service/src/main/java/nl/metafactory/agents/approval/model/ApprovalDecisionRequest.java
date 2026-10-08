package nl.metafactory.agents.approval.model;

/** Request-body shape for {@code POST /api/agent-runs/{runId}/approval-gate/decision} (embabel side). */
public record ApprovalDecisionRequest(ApprovalDecisionKind decision, int iteration, String comment) {
}
