package nl.metafactory.aicontrol.model;

import java.time.Instant;
import java.util.UUID;

public record GitWorkspaceJobEventDto(
        UUID id,
        UUID jobId,
        String eventType,
        String message,
        Instant createdAt
) {}
