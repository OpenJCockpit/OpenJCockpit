package nl.metafactory.aicontrol.client;

import java.time.Instant;

/** An A2A service as relayed from embabel-agent-service; secrets are never part of it. */
public record A2AServiceDto(
        String id,
        String name,
        String type,
        String source,
        boolean editable,
        String baseUrl,
        String publicUrl,
        boolean hasApiKey,
        String callerTokenUrl,
        String callerClientId,
        boolean hasCallerClientSecret,
        String status,
        String message,
        Integer agentCount,
        Instant updatedAt
) {
}
