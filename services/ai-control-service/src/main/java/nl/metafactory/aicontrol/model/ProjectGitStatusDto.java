package nl.metafactory.aicontrol.model;

import java.time.Instant;
import java.util.UUID;

public record ProjectGitStatusDto(
        UUID projectId,
        GitStatus gitStatus,
        Instant gitStatusCheckedAt,
        String gitStatusMessage
) {}
