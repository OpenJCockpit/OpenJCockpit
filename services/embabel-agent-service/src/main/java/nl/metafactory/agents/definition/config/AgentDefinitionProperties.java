package nl.metafactory.agents.definition.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Single configuration group for configuration-driven agent definitions (SPEC-002).
 *
 * <p>Secrets are never set here directly: {@code litellm.model-gateway-key} is bound from the
 * {@code LITELLM_VIRTUAL_KEY} environment variable. The LiteLLM registry/A2A connection is the
 * shared A2A provider client ({@code openjcockpit.a2a-providers}). Every default
 * is a Java field initialiser, and the feature is disabled by default, so an absent YAML block
 * never yields a permissive configuration.
 */
@Component
@Validated
@ConfigurationProperties("openjcockpit.agent-definitions")
public class AgentDefinitionProperties {

    /** Where a definition may come from, in the order the resolver tries them. */
    public enum SourceKind { REMOTE, LAST_KNOWN_GOOD, LOCAL }

    private boolean enabled = false;

    /** Root containing {@code agents/} and {@code skills/}; required when enabled. */
    private String localRoot;

    @NotEmpty
    private List<SourceKind> sourcePriority = new ArrayList<>(
            List.of(SourceKind.REMOTE, SourceKind.LAST_KNOWN_GOOD, SourceKind.LOCAL));

    /** JWT claim carrying the caller's tenant id; tokens without it are rejected. */
    @NotEmpty
    private String tenantClaim = "tenant_id";

    /** Guardrail ids every agent must have enforced, whatever its definition says. */
    @NotNull
    private List<String> mandatoryGuardrails = new ArrayList<>();

    @Valid
    @NotNull
    private Fallback fallback = new Fallback();

    @Valid
    @NotNull
    private Cache cache = new Cache();

    @Valid
    @NotNull
    private LiteLlm litellm = new LiteLlm();

    @Valid
    @NotNull
    private A2a a2a = new A2a();

    @AssertTrue(message = "openjcockpit.agent-definitions.local-root is required when the feature is enabled")
    public boolean isLocalRootPresentWhenEnabled() {
        return !enabled || (localRoot != null && !localRoot.isBlank());
    }

    @AssertTrue(message = "openjcockpit.agent-definitions.a2a.enabled requires openjcockpit.agent-definitions.enabled")
    public boolean isA2aOnlyWhenEnabled() {
        return a2a == null || !a2a.isEnabled() || enabled;
    }

    @AssertTrue(message = "openjcockpit.agent-definitions.source-priority must not contain duplicates")
    public boolean isSourcePriorityUnique() {
        return sourcePriority == null || sourcePriority.stream().distinct().count() == sourcePriority.size();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(String localRoot) {
        this.localRoot = localRoot;
    }

    public List<SourceKind> getSourcePriority() {
        return sourcePriority;
    }

    public void setSourcePriority(List<SourceKind> sourcePriority) {
        this.sourcePriority = sourcePriority;
    }

    public String getTenantClaim() {
        return tenantClaim;
    }

    public void setTenantClaim(String tenantClaim) {
        this.tenantClaim = tenantClaim;
    }

    public List<String> getMandatoryGuardrails() {
        return mandatoryGuardrails;
    }

    public void setMandatoryGuardrails(List<String> mandatoryGuardrails) {
        this.mandatoryGuardrails = mandatoryGuardrails;
    }

    public Fallback getFallback() {
        return fallback;
    }

    public void setFallback(Fallback fallback) {
        this.fallback = fallback;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public LiteLlm getLitellm() {
        return litellm;
    }

    public void setLitellm(LiteLlm litellm) {
        this.litellm = litellm;
    }

    public A2a getA2a() {
        return a2a;
    }

    public void setA2a(A2a a2a) {
        this.a2a = a2a;
    }

    /**
     * Which source failures may fall through to the next source. {@code UNAUTHORIZED} and
     * {@code INVALID} are deliberately not configurable: they never fall back.
     */
    public static class Fallback {

        private boolean onUnavailable = true;

        private boolean onNotFound = false;

        public boolean isOnUnavailable() {
            return onUnavailable;
        }

        public void setOnUnavailable(boolean onUnavailable) {
            this.onUnavailable = onUnavailable;
        }

        public boolean isOnNotFound() {
            return onNotFound;
        }

        public void setOnNotFound(boolean onNotFound) {
            this.onNotFound = onNotFound;
        }
    }

    /** Last-known-good snapshot retention. */
    public static class Cache {

        @NotNull
        private Duration ttl = Duration.ofHours(1);

        private int maxEntries = 256;

        /** Directory for last-known-good snapshots; when unset, no last-known-good source exists. */
        private String directory;

        @AssertTrue(message = "cache.ttl must be positive and cache.max-entries must be > 0")
        public boolean isPositive() {
            return ttl != null && !ttl.isNegative() && !ttl.isZero() && maxEntries > 0;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public int getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }
    }

    /** LiteLLM model-gateway settings for agent model aliases and gateway guardrails. */
    public static class LiteLlm {

        /**
         * Guardrail ids enforced by the LiteLLM gateway, mapped to the LiteLLM guardrail name that a
         * policy attachment must apply to the agent's model alias (verified via
         * {@code POST /policies/resolve} through the LiteLLM A2A provider client, fail closed).
         */
        @NotNull
        private java.util.Map<String, String> gatewayGuardrails = new java.util.LinkedHashMap<>();

        /** Model aliases routed as separate LiteLLM models, so policies can attach per alias. */
        @NotNull
        private List<String> gatewayModelAliases = new ArrayList<>();

        /** OpenAI-compatible model gateway URL (with {@code /v1}) used for those aliases. */
        private String modelGatewayUrl;

        /** Virtual key for model calls (env {@code LITELLM_VIRTUAL_KEY}); not the A2A provider key. */
        private String modelGatewayKey;

        public java.util.Map<String, String> getGatewayGuardrails() {
            return gatewayGuardrails;
        }

        public void setGatewayGuardrails(java.util.Map<String, String> gatewayGuardrails) {
            this.gatewayGuardrails = gatewayGuardrails;
        }

        public List<String> getGatewayModelAliases() {
            return gatewayModelAliases;
        }

        public void setGatewayModelAliases(List<String> gatewayModelAliases) {
            this.gatewayModelAliases = gatewayModelAliases;
        }

        public String getModelGatewayUrl() {
            return modelGatewayUrl;
        }

        public void setModelGatewayUrl(String modelGatewayUrl) {
            this.modelGatewayUrl = modelGatewayUrl;
        }

        public String getModelGatewayKey() {
            return modelGatewayKey;
        }

        public void setModelGatewayKey(String modelGatewayKey) {
            this.modelGatewayKey = modelGatewayKey;
        }

        @Override
        public String toString() {
            return "LiteLlm{modelGatewayUrl=" + modelGatewayUrl + ", modelGatewayKey="
                    + (modelGatewayKey == null ? "null" : "***") + "}";
        }
    }

    /** Inbound A2A server settings. */
    public static class A2a {

        private boolean enabled = false;

        /** Externally reachable base URL advertised in the Agent Card. */
        private String publicBaseUrl;

        @AssertTrue(message = "a2a.public-base-url is required when a2a.enabled=true")
        public boolean isConfiguredWhenEnabled() {
            return !enabled || (publicBaseUrl != null && !publicBaseUrl.isBlank());
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
        }
    }
}
