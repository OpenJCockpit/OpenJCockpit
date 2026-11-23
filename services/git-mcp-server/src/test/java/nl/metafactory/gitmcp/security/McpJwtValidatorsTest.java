package nl.metafactory.gitmcp.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** The claim validators on their own, without signatures (the decoder tests cover those). */
class McpJwtValidatorsTest {

    private static final String ISSUER = "https://auth.example.test/realms/openjcockpit";

    private final OAuth2TokenValidator<Jwt> validator = McpJwtValidators.forProperties(properties());

    private static McpSecurityProperties properties() {
        var properties = new McpSecurityProperties();
        properties.setKeycloakEnabled("true");
        properties.setIssuerUri(ISSUER);
        properties.setExpectedAudience("openjcockpit-git-mcp");
        properties.setAllowedAzp("openjcockpit-litellm-git,openjcockpit-agent-service");
        properties.afterPropertiesSet();
        return properties;
    }

    private static Jwt jwt(Consumer<Jwt.Builder> customizer) {
        Instant now = Instant.now();
        var builder = Jwt.withTokenValue("token").header("alg", "RS256").header("typ", "JWT")
                .issuer(ISSUER)
                .audience(List.of("openjcockpit-git-mcp"))
                .claim("azp", "openjcockpit-litellm-git")
                .issuedAt(now)
                .notBefore(now.minusSeconds(5))
                .expiresAt(now.plusSeconds(300));
        customizer.accept(builder);
        return builder.build();
    }

    private boolean valid(Consumer<Jwt.Builder> customizer) {
        return !validator.validate(jwt(customizer)).hasErrors();
    }

    @Test
    void acceptsATokenThatPassesEveryCheck() {
        assertThat(valid(jwt -> { })).isTrue();
    }

    @Test
    void acceptsEveryAllowlistedClientAndAudienceAmongSeveral() {
        assertThat(valid(jwt -> jwt.claim("azp", "openjcockpit-agent-service"))).isTrue();
        assertThat(valid(jwt -> jwt.audience(List.of("account", "openjcockpit-git-mcp", "other")))).isTrue();
    }

    @Test
    void issuerMustMatchExactly() {
        assertThat(valid(jwt -> jwt.issuer("https://evil.example.test/realms/openjcockpit"))).isFalse();
        assertThat(valid(jwt -> jwt.issuer(ISSUER + "/"))).isFalse();
        assertThat(valid(jwt -> jwt.claims(claims -> claims.remove("iss")))).isFalse();
    }

    @Test
    void audienceMustBePresentAndMatchExactly() {
        assertThat(valid(jwt -> jwt.claims(claims -> claims.remove("aud")))).isFalse();
        assertThat(valid(jwt -> jwt.audience(List.of("account")))).isFalse();
        assertThat(valid(jwt -> jwt.audience(List.of("openjcockpit-git-mcp-extra")))).isFalse();
        assertThat(valid(jwt -> jwt.audience(List.of("openjcockpit-git")))).isFalse();
    }

    @Test
    void azpMustBePresentNonBlankAndAnExactAllowlistMember() {
        assertThat(valid(jwt -> jwt.claims(claims -> claims.remove("azp")))).isFalse();
        assertThat(valid(jwt -> jwt.claim("azp", " "))).isFalse();
        assertThat(valid(jwt -> jwt.claim("azp", "openjcockpit-dashboard"))).isFalse();
        assertThat(valid(jwt -> jwt.claim("azp", "openjcockpit-litellm-git-evil"))).isFalse();
        assertThat(valid(jwt -> jwt.claim("azp", "litellm"))).isFalse();
        assertThat(valid(jwt -> jwt.claim("azp", List.of("openjcockpit-litellm-git")))).isFalse();
    }

    @Test
    void expiredAndNotYetValidTokensAreRefused() {
        Instant now = Instant.now();
        assertThat(valid(jwt -> jwt.issuedAt(now.minusSeconds(900)).notBefore(now.minusSeconds(900))
                .expiresAt(now.minusSeconds(600)))).isFalse();
        assertThat(valid(jwt -> jwt.notBefore(now.plusSeconds(600)))).isFalse();
    }

    @Test
    void authorizedPartyValidatorAloneUsesExactMembership() {
        var azp = McpJwtValidators.authorizedParty(Set.of("a"));

        assertThat(azp.validate(jwt(jwt -> jwt.claim("azp", "a"))).hasErrors()).isFalse();
        assertThat(azp.validate(jwt(jwt -> jwt.claim("azp", "A"))).hasErrors()).isTrue();
        assertThat(azp.validate(jwt(jwt -> jwt.claim("azp", "ab"))).hasErrors()).isTrue();
    }
}
