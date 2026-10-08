package nl.metafactory.aicontrol.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SpecQueueItemDto(
        UUID id,
        UUID projectId,
        String specFile,
        String workflowId,
        String workflowName,
        boolean autoMerge,
        Integer position,
        SpecQueueItemStatus status,
        SpecQueueFailureReason failureReason,
        String workflowRunId,
        String runStatus,
        SpecQueueMergeState mergeState,
        List<String> pullRequestUrls,
        String createdBy,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {}
