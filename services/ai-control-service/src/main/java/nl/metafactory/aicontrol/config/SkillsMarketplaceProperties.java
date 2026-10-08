package nl.metafactory.aicontrol.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Externalized policy for the skills-marketplace egress path (Frozen Contract #2 in the
 * skills-tab-marketplace-import work plan). Every default is also a Java field initialiser, so a
 * truncated or absent YAML block cannot silently produce a permissive value (BR-14).
 *
 * <p>{@link Egress#allowPrivateAddresses} defaults to {@code false} (deny). It is the single,
 * narrowly-scoped opt-in that relaxes loopback/RFC1918/link-local/CGNAT/IPv6-ULA destinations so
 * the feature can be exercised against a local/dev-only marketplace double. Cloud-metadata
 * endpoints stay hard-blocked regardless of this flag (ADR-7) — see {@code EgressGuard}. This
 * flag must never be enabled in a deployed environment.</p>
 */
@Validated
@ConfigurationProperties(prefix = "metafactory.skills-marketplace")
public class SkillsMarketplaceProperties {

    private int requestTimeoutSeconds = 5;
    private int connectTimeoutSeconds = 2;
    private long maxResponseBytes = 262_144;
    private int maxItems = 500;
    private int maxRedirects = 1;
    private int maxConnectionsPerFetch = 25;
    private Egress egress = new Egress();

    public static class Egress {
        private boolean allowPrivateAddresses = false;

        public boolean isAllowPrivateAddresses() {
            return allowPrivateAddresses;
        }

        public void setAllowPrivateAddresses(boolean allowPrivateAddresses) {
            this.allowPrivateAddresses = allowPrivateAddresses;
        }
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public long getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public void setMaxResponseBytes(long maxResponseBytes) {
        this.maxResponseBytes = maxResponseBytes;
    }

    public int getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = maxItems;
    }

    public int getMaxRedirects() {
        return maxRedirects;
    }

    public void setMaxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
    }

    public int getMaxConnectionsPerFetch() {
        return maxConnectionsPerFetch;
    }

    public void setMaxConnectionsPerFetch(int maxConnectionsPerFetch) {
        this.maxConnectionsPerFetch = maxConnectionsPerFetch;
    }

    public Egress getEgress() {
        return egress;
    }

    public void setEgress(Egress egress) {
        this.egress = egress;
    }
}
