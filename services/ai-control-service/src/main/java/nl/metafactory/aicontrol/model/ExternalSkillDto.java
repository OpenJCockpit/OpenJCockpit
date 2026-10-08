package nl.metafactory.aicontrol.model;

import java.util.UUID;

/**
 * A skill offered by an external marketplace, as exposed on {@code GET /api/skill-catalog}
 * (Frozen Contract #1). Deliberately has exactly four fields — no {@code inputContract},
 * {@code outputContract}, {@code executionInstructions}, {@code mcpTools} or {@code policyNotes}
 * — so that BR-17/AC-58 ("Not provided" for input/output) and AC-23 (no edit form can be
 * prefilled with external data) are type-level facts rather than runtime checks (ADR-4).
 */
public record ExternalSkillDto(
        UUID marketplaceId,
        String marketplaceName,
        String name,
        String description
) {
}
