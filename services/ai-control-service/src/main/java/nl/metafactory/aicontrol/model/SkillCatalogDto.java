package nl.metafactory.aicontrol.model;

import nl.metafactory.aicontrol.client.SkillSpecDto;

import java.util.List;

/**
 * Response body of {@code GET /api/skill-catalog} (Frozen Contract #1, architecture ADR-4).
 *
 * <p>{@code localSkills} is the unmodified, existing {@link SkillSpecDto} — no {@code origin}
 * field is added to it, so {@code GET /api/skill-definitions} (AC-33) needs no change at all.
 * {@code externalSkills} and {@code sources} are additive, new types. Arrays are always present;
 * an empty result is {@code []}, never {@code null} (BR-6, BR-7).</p>
 */
public record SkillCatalogDto(
        List<SkillSpecDto> localSkills,
        List<ExternalSkillDto> externalSkills,
        List<SkillCatalogSourceStatusDto> sources
) {
}
