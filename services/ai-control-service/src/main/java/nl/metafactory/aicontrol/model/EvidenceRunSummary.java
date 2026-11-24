package nl.metafactory.aicontrol.model;

import java.time.Instant;

public record EvidenceRunSummary(
        String runId,
        String workflowId,
        String status,
        Instant startedAt,
        Instant completedAt,
        String startedBy,
        String branch,
        Integer eventCount,
        Integer failedEventCount
) {}
