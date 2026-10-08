package nl.metafactory.aicontrol.integration.skillsmarketplace;

import nl.metafactory.aicontrol.model.SkillCatalogSourceOutcome;

import java.util.List;

/**
 * Outward output of the adapter seam (ADR-1). Carries no marketplace-supplied string — only the
 * vendor-neutral {@link SkillCatalogSourceOutcome}, an optional HTTP status, and the mapped
 * skills.
 */
public record MarketplaceCatalog(SkillCatalogSourceOutcome outcome, Integer httpStatus, List<ExternalSkill> skills) {

    public static MarketplaceCatalog success(int httpStatus, List<ExternalSkill> skills) {
        return new MarketplaceCatalog(SkillCatalogSourceOutcome.SUCCESS, httpStatus, skills);
    }

    public static MarketplaceCatalog failure(SkillCatalogSourceOutcome outcome, Integer httpStatus) {
        return new MarketplaceCatalog(outcome, httpStatus, List.of());
    }
}
