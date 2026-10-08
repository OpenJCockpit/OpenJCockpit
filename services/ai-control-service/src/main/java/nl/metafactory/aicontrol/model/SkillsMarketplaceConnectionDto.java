package nl.metafactory.aicontrol.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Response shape for a skills marketplace connection. Deliberately has no field that carries the
 * API key in any form (not even masked/truncated) — see BR-4 in the requirements document.
 */
public record SkillsMarketplaceConnectionDto(
        UUID id,
        String name,
        String marketplaceUrl,
        String description,
        boolean enabled,
        boolean hasApiKey,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {}
