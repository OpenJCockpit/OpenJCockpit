package nl.metafactory.aicontrol.service;

import java.util.UUID;

/**
 * Result of resolving one active+enabled {@code skills_marketplace_connections} row into
 * something the fetch cycle can act on (architecture §6.5). {@link Unusable} connections are
 * reported as a {@code CONFIG_ERROR} source with <em>no</em> outbound request ever attempted
 * (BR-2, AC-47).
 */
public sealed interface MarketplaceQueryTarget {

    /**
     * A connection with a successfully decrypted, non-blank API key. {@code toString()} is
     * overridden to emit only id and name — a record's synthesized {@code toString()} would
     * otherwise print the plaintext key into any accidental log line (BR-8, AC-26).
     */
    record Queryable(UUID id, String name, String marketplaceUrl, String apiKey) implements MarketplaceQueryTarget {
        @Override
        public String toString() {
            return "Queryable[id=" + id + ", name=" + name + "]";
        }
    }

    /** A connection whose key is missing or could not be decrypted. */
    record Unusable(UUID id, String name, String reason) implements MarketplaceQueryTarget {
    }
}
