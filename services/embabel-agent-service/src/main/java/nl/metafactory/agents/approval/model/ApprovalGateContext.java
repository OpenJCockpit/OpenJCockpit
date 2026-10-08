package nl.metafactory.agents.approval.model;

import java.time.Instant;
import java.util.List;

/**
 * Domain form of {@code ApprovalGateContextDto} (Frozen Contract #1). Field order matches the
 * frozen wire contract verbatim for easy cross-checking against the frontend's/relay's DTO.
 */
public record ApprovalGateContext(
        String runId,
        String workflowId,
        String placementStage,
        StageOutcome stageOutcome,
        String outcomeReason,
        String changeSummary,
        List<String> changedPaths,
        int changedFileCount,
        int omittedFileCount,
        String branchName,
        String pullRequestUrl,
        String branchCompareUrl,
        int iteration,
        int maxFeedbackIterations,
        boolean feedbackSupported,
        boolean furtherFeedbackAllowed,
        List<GateComment> commentHistory,
        Instant openedAt
) {
}
