package nl.metafactory.agents.approval.model;

import java.time.Instant;

/**
 * Durable audit record for one decided gate iteration (ADR-004, V6, BR-31): a dedicated type in
 * its own sibling directory, deliberately NOT combined with the pre-existing policy audit record,
 * whose own javadoc restricts it to "identifiers, labels, and classifications" and excludes
 * payload content. Field order matches {@code ApprovalDecisionAuditEntryDto} (Frozen Contract #1) verbatim.
 */
public record ApprovalDecisionAuditEntry(
        String id,
        String runId,
        String workflowId,
        String gateStage,
        int iteration,
        ApprovalDecisionKind decision,
        String comment,
        String actorUsername,
        String actorSubject,
        StageOutcome stageOutcome,
        String branchName,
        String pullRequestUrl,
        Instant timestamp
) {
}
