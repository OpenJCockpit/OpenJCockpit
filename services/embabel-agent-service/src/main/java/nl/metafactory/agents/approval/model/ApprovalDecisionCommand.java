package nl.metafactory.agents.approval.model;

/**
 * The actor is an explicit field here — not read from any ambient context — so a future
 * {@code @PreAuthorize} on the controller needs no contract change (AC-38 seam, architecture §8.2).
 * Identity always comes from the caller's JWT, never a client-supplied field (V5); the controller
 * is the only place that constructs this record.
 */
public record ApprovalDecisionCommand(
        ApprovalDecisionKind decision,
        int iteration,
        String comment,
        String actorUsername,
        String actorSubject
) {
}
