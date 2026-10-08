package nl.metafactory.aicontrol.model;

/**
 * Distinguishes the two kinds of entries that can appear in {@link SkillCatalogDto#sources()}:
 * the single, always-present local source, and one entry per queried marketplace connection.
 */
public enum SkillCatalogSourceKind {
    LOCAL,
    MARKETPLACE
}
