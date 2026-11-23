package nl.metafactory.gitmcp.security;

import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

import java.util.Set;

/**
 * Claim validation of incoming MCP access tokens, composed only from Spring Security's standard validators:
 * the defaults ({@code typ}, {@code exp}/{@code nbf} with Spring's default clock skew, X.509 thumbprint), the
 * exact issuer, the expected audience among the {@code aud} values, and the caller's client id ({@code azp})
 * in the allowlist. Every validator must pass; the signature itself is verified by the decoder.
 */
final class McpJwtValidators {

    static final String AZP = "azp";

    private McpJwtValidators() {
    }

    static OAuth2TokenValidator<Jwt> forProperties(McpSecurityProperties properties) {
        return JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(properties.getIssuerUri()),
                new JwtAudienceValidator(properties.getExpectedAudience()),
                authorizedParty(properties.allowedClientIds()));
    }

    /** {@code azp} must be present, non-blank and exactly one of {@code allowedClientIds} (no patterns). */
    static OAuth2TokenValidator<Jwt> authorizedParty(Set<String> allowedClientIds) {
        Set<String> allowed = Set.copyOf(allowedClientIds);
        return new JwtClaimValidator<Object>(AZP,
                azp -> azp instanceof String clientId && !clientId.isBlank() && allowed.contains(clientId));
    }
}
