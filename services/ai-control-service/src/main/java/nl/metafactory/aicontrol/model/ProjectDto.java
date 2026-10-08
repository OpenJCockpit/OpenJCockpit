package nl.metafactory.aicontrol.model;

import java.time.Instant;
import java.util.UUID;

public record ProjectDto(
        UUID id,
        String name,
        String customerId,
        String gitUrl,
        String description,
        String defaultBranch,
        String environment,
        String owner,
        short active,
        short newProject,
        GitStatus gitStatus,
        Instant gitStatusCheckedAt,
        String gitStatusMessage,
        boolean hasCredentials,
        Instant createdAt,
        Instant updatedAt
) {}
