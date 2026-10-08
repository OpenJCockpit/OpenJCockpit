package nl.metafactory.aicontrol.client;

import java.time.Instant;
import java.util.List;

public record ApprovalGateContextDto(
        String runId,
        String workflowId,
        String placementStage,
        ApprovalStageOutcome stageOutcome,
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
        List<ApprovalGateCommentDto> commentHistory,
        Instant openedAt
) {
}
