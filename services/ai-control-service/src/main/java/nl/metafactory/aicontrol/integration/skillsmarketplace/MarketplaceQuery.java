package nl.metafactory.aicontrol.integration.skillsmarketplace;

import java.util.UUID;

/**
 * Inward input to the adapter seam (ADR-1). Carries the decrypted marketplace API key — {@link
 * #toString()} is deliberately overridden to emit only id and name, because a record's
 * synthesized {@code toString()} would otherwise print the plaintext key into any accidental log
 * line or exception message (BR-8, AC-26).
 */
public record MarketplaceQuery(UUID connectionId, String connectionName, String marketplaceUrl, String apiKey) {

    @Override
    public String toString() {
        return "MarketplaceQuery[connectionId=" + connectionId + ", connectionName=" + connectionName + "]";
    }
}
