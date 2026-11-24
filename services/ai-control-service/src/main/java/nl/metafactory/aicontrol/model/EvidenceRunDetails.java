package nl.metafactory.aicontrol.model;

import java.time.Instant;
import java.util.List;

public record EvidenceRunDetails(
        String runId,
        String workflowId,
        String status,
        String startedBy,
        Instant startedAt,
        Instant completedAt,
        String branch,
        String commitHash,
        List<String> artifacts,
        String failureSummary
) {}
