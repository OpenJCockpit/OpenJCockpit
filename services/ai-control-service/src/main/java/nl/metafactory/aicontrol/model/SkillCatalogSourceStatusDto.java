package nl.metafactory.aicontrol.model;

import java.util.UUID;

/**
 * Per-source fetch status on {@code GET /api/skill-catalog} (Frozen Contract #1). Carries no
 * marketplace-supplied string: only our own database {@code name}, our own {@link
 * SkillCatalogSourceOutcome} enum, and two integers (BR-8, AC-26). {@code marketplaceId} is
 * {@code null} only for the {@link SkillCatalogSourceKind#LOCAL} entry; {@code httpStatus} is
 * {@code null} whenever no HTTP response was actually received.
 */
public record SkillCatalogSourceStatusDto(
        SkillCatalogSourceKind kind,
        UUID marketplaceId,
        String name,
        SkillCatalogSourceOutcome outcome,
        int itemCount,
        Integer httpStatus
) {
}
