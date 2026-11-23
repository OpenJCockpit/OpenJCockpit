package nl.metafactory.gitmcp.security;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Authentication of INCOMING MCP requests (MCP client / LiteLLM / OpenJCockpit -> this server). It has nothing
 * to do with the outgoing service identity this server uses towards ai-control-service for workspace
 * credentials ({@code openjcockpit.git-mcp.credentials}).
 *
 * <p>Validated at startup and fail-closed: {@code keycloak-enabled} must be exactly {@code true} or
 * {@code false}, and when it is {@code true} the issuer, audience and AZP allowlist must all be set. When it is
 * {@code false} the other values are ignored, so unused Keycloak settings never break startup. Violations name
 * the environment variable, never a value.
 */
@Component
@ConfigurationProperties("openjcockpit.git-mcp.security")
public class McpSecurityProperties implements InitializingBean {

    /** Prefix and property name of the switch, shared with the {@code @ConditionalOnProperty} of the two chains. */
    static final String PREFIX = "openjcockpit.git-mcp.security";
    static final String ENABLED_PROPERTY = "keycloak-enabled";

    /**
     * {@code true}: every MCP request needs a valid Keycloak JWT. {@code false}: every request is permitted
     * without a token (local development or a trusted, network-isolated deployment only). Kept as text so a
     * value such as {@code yes} or {@code 1} is refused instead of silently picking a mode.
     */
    private String keycloakEnabled = "false";

    /** Exact expected {@code iss} claim (the Keycloak realm URL as clients see it). */
    private String issuerUri;

    /**
     * Optional JWKS URL for the signing keys (e.g. Keycloak's internal Docker/Kubernetes address). The
     * {@code iss} claim is still validated against {@link #issuerUri}. Blank: discovered from the issuer.
     */
    private String jwkSetUri;

    /** Audience that must be one of the token's {@code aud} values. */
    private String expectedAudience;

    /** Comma-separated Keycloak client ids allowed as the token's {@code azp} (exact match). */
    private String allowedAzp;

    @Override
    public void afterPropertiesSet() {
        boolean enabled = keycloakEnabled();
        if (!enabled) {
            return;
        }
        List<String> violations = new ArrayList<>();
        if (isBlank(issuerUri)) {
            violations.add("KEYCLOAK_ISSUER_URI is not set");
        } else if (!isAbsoluteHttpUri(issuerUri)) {
            violations.add("KEYCLOAK_ISSUER_URI is not an absolute http(s) URL");
        }
        if (!isBlank(jwkSetUri) && !isAbsoluteHttpUri(jwkSetUri)) {
            violations.add("KEYCLOAK_JWK_SET_URI is not an absolute http(s) URL");
        }
        if (isBlank(expectedAudience)) {
            violations.add("KEYCLOAK_EXPECTED_AUDIENCE is not set");
        }
        if (allowedClientIds().isEmpty()) {
            violations.add("KEYCLOAK_ALLOWED_AZP is not set");
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Incoming MCP authentication is enabled (KEYCLOAK_ENABLED=true) but "
                    + "misconfigured: " + String.join("; ", violations));
        }
    }

    /**
     * The parsed switch; anything other than {@code true}/{@code false} (case-insensitive, no surrounding
     * whitespace, the same rule as {@code @ConditionalOnProperty}) is refused, so the chosen filter chain always
     * agrees with this value.
     */
    public boolean keycloakEnabled() {
        String value = keycloakEnabled == null ? "" : keycloakEnabled.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalStateException(
                    "KEYCLOAK_ENABLED must be 'true' or 'false' (got an unsupported value)");
        };
    }

    /** {@link #allowedAzp} split on commas, trimmed, blanks dropped; order kept for logging. */
    public Set<String> allowedClientIds() {
        if (allowedAzp == null) {
            return Set.of();
        }
        var ids = new LinkedHashSet<String>();
        Arrays.stream(allowedAzp.split(",")).map(String::trim).filter(id -> !id.isEmpty()).forEach(ids::add);
        return Collections.unmodifiableSet(ids);
    }

    public boolean hasJwkSetUri() {
        return !isBlank(jwkSetUri);
    }

    private static boolean isAbsoluteHttpUri(String value) {
        try {
            URI uri = new URI(value.trim());
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public String getKeycloakEnabled() { return keycloakEnabled; }
    public void setKeycloakEnabled(String keycloakEnabled) { this.keycloakEnabled = keycloakEnabled; }
    public String getIssuerUri() { return issuerUri; }
    public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }
    public String getJwkSetUri() { return jwkSetUri; }
    public void setJwkSetUri(String jwkSetUri) { this.jwkSetUri = jwkSetUri; }
    public String getExpectedAudience() { return expectedAudience; }
    public void setExpectedAudience(String expectedAudience) { this.expectedAudience = expectedAudience; }
    public String getAllowedAzp() { return allowedAzp; }
    public void setAllowedAzp(String allowedAzp) { this.allowedAzp = allowedAzp; }
}
