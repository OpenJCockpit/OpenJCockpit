package nl.metafactory.aicontrol.model;

/**
 * Vendor-neutral outcome vocabulary for one fetch attempt against a skill source (local or
 * marketplace). Single-sourced across the adapter seam ({@code SkillsMarketplaceClient}) and the
 * OpenAPI contract (architecture §6.2). A superset of the six values enumerated by AC-41, adding
 * {@code UNREACHABLE} because AC-11 requires distinguishing "unreachable" from "timeout" (O-2).
 */
public enum SkillCatalogSourceOutcome {
    SUCCESS,
    AUTH_FAILED,
    UNREACHABLE,
    TIMEOUT,
    INVALID_RESPONSE,
    BLOCKED_BY_POLICY,
    CONFIG_ERROR
}
