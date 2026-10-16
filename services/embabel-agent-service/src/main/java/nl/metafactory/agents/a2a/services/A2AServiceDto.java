package nl.metafactory.agents.a2a.services;

import java.time.Instant;

/**
 * An A2A service as shown in the dashboard. {@code source} is {@code CONFIGURATION} (read-only, from
 * {@code openjcockpit.a2a-providers}) or {@code DASHBOARD}. Secrets are never returned, only whether
 * they are set. {@code status} is the live catalog status (OK, NOT_CONFIGURED, UNAVAILABLE, UNAUTHORIZED,
 * INVALID); {@code agentCount} is set when OK.
 */
public record A2AServiceDto(String id, String name, String type, String source, boolean editable, String baseUrl,
                            String publicUrl, boolean hasApiKey, String callerTokenUrl, String callerClientId,
                            boolean hasCallerClientSecret, String status, String message, Integer agentCount,
                            Instant updatedAt) {
}
