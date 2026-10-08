package nl.metafactory.aicontrol.integration.skillsmarketplace;

/**
 * The single swappable adapter seam (ADR-1, BR-13). Exactly one implementation exists in this
 * delivery ({@code placeholder.PlaceholderSkillsMarketplaceClient}); substituting a real vendor
 * implementation requires no change to the merge/origin logic, the OpenAPI contract, or the
 * frontend (AC-48).
 */
public interface SkillsMarketplaceClient {

    MarketplaceCatalog fetchCatalog(MarketplaceQuery query);
}
