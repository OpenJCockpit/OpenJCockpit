package nl.metafactory.aicontrol.client;

import java.time.Instant;

public record ApprovalDecisionAuditEntryDto(
        String id,
        String runId,
        String workflowId,
        String gateStage,
        int iteration,
        ApprovalDecisionKind decision,
        String comment,
        String actorUsername,
        String actorSubject,
        ApprovalStageOutcome stageOutcome,
        String branchName,
        String pullRequestUrl,
        Instant timestamp
) {
}
