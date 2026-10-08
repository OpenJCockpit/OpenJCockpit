package nl.metafactory.aicontrol.model;

import java.time.Instant;
import java.util.UUID;

public record GitWorkspaceJobDto(
        UUID id,
        UUID projectId,
        String specFileRef,
        GitWorkspaceJobStatus status,
        String baseBranch,
        String workingBranch,
        String commitHash,
        GitWorkspaceJobErrorCode errorCode,
        String errorMessage,
        String startedBy,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {}
