package nl.metafactory.agents.approval.model;

/** Field order matches {@code ApprovalDecisionResultDto} (Frozen Contract #1) verbatim. */
public record ApprovalDecisionResult(String runId, String status, int iteration, String message) {
}
