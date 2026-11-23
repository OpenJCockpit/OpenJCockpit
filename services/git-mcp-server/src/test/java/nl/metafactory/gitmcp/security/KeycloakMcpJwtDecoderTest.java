package nl.metafactory.gitmcp.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The decoder bean against signed tokens and a JWKS served over HTTP (both key-source modes). */
class KeycloakMcpJwtDecoderTest {

    private final TestKeycloak keycloak = new TestKeycloak();

    @AfterEach
    void stop() {
        keycloak.close();
    }

    private JwtDecoder decoder(boolean explicitJwkSetUri) {
        return new KeycloakMcpSecurityConfig().mcpJwtDecoder(keycloak.enabledProperties(explicitJwkSetUri));
    }

    @Test
    void explicitJwkSetUriDecodesAValidToken() {
        var jwt = decoder(true).decode(keycloak.validToken());

        assertThat(jwt.getClaimAsString("azp")).isEqualTo(TestKeycloak.LITELLM_CLIENT);
        assertThat(jwt.getAudience()).containsExactly(TestKeycloak.AUDIENCE);
    }

    @Test
    void issuerDiscoveryDecodesAValidToken() {
        var jwt = decoder(false).decode(keycloak.validToken());

        assertThat(jwt.getIssuer().toString()).isEqualTo(keycloak.issuer());
    }

    @Test
    void jwkSetUriDoesNotReplaceTheIssuerCheck() {
        // Keys come from the (internal) JWKS URL, but iss must still be the configured public issuer.
        var token = keycloak.token(claims -> claims.issuer(keycloak.jwkSetUri()));

        assertThatThrownBy(() -> decoder(true).decode(token)).isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("iss");
    }

    @Test
    void refusesEveryInvalidToken() {
        JwtDecoder decoder = decoder(true);
        Instant now = Instant.now();

        assertThatThrownBy(() -> decoder.decode(keycloak.tokenSignedWithOtherKey())).isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> decoder.decode(keycloak.unsignedToken())).isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> decoder.decode("not-a-jwt")).isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> decoder.decode(keycloak.token(claims -> claims
                .issueTime(Date.from(now.minusSeconds(900))).notBeforeTime(Date.from(now.minusSeconds(900)))
                .expirationTime(Date.from(now.minusSeconds(600)))))).isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode(keycloak.token(claims -> claims
                .notBeforeTime(Date.from(now.plusSeconds(600)))))).isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode(keycloak.token(claims -> claims.audience(List.of("account")))))
                .isInstanceOf(JwtValidationException.class).hasMessageContaining("aud");
        assertThatThrownBy(() -> decoder.decode(keycloak.token(claims -> claims.claim("azp", "openjcockpit-dashboard"))))
                .isInstanceOf(JwtValidationException.class).hasMessageContaining("azp");
    }

    @Test
    void unreachableSigningKeysRefuseTheToken() {
        JwtDecoder decoder = decoder(true);
        String token = keycloak.validToken();
        keycloak.close();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void issuerDiscoveryIsDeferredToTheFirstTokenAndRetried() {
        var properties = keycloak.enabledProperties(false);
        String token = keycloak.validToken();
        keycloak.close();

        // Building the decoder does not contact the issuer; decoding does, and fails closed while it is down.
        JwtDecoder decoder = new KeycloakMcpSecurityConfig().mcpJwtDecoder(properties);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(RuntimeException.class);
    }
}
