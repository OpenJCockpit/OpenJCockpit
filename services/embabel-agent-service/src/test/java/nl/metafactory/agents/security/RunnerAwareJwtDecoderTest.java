package nl.metafactory.agents.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunnerAwareJwtDecoderTest {

    private static final String SECRET = "test-only-spec-queue-runner-secret-32-bytes-minimum";
    private static final String ISSUER = "urn:openjcockpit:ai-control-service:spec-queue-runner";

    private final Jwt keycloakJwt = Jwt.withTokenValue("kc").header("alg", "RS256").claim("sub", "human").build();
    private final JwtDecoder primary = token -> {
        if (token.equals("kc")) return keycloakJwt;
        throw new BadJwtException("primary rejected");
    };
    private final SpecQueueRunnerTokenProperties props = new SpecQueueRunnerTokenProperties();
    private final RunnerAwareJwtDecoder decoder;

    RunnerAwareJwtDecoderTest() {
        props.setSecret(SECRET);
        decoder = new RunnerAwareJwtDecoder(primary, props);
    }

    private static JWTClaimsSet.Builder claims(String issuer, Instant iat, Instant exp) {
        var b = new JWTClaimsSet.Builder().issuer(issuer).audience(List.of("embabel-agent-service"))
                .subject("spec-queue-runner").claim("preferred_username", "spec-queue-runner");
        if (iat != null) b.issueTime(Date.from(iat));
        if (exp != null) b.expirationTime(Date.from(exp));
        return b;
    }

    private static String sign(JWTClaimsSet claims, String secret) throws Exception {
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes()));
        return jwt.serialize();
    }

    private String valid() throws Exception {
        Instant now = Instant.now();
        return sign(claims(ISSUER, now, now.plusSeconds(60)).build(), SECRET);
    }

    @Test
    void validRunnerTokenDecodes() throws Exception {
        Jwt jwt = decoder.decode(valid());
        assertThat(jwt.getClaimAsString("preferred_username")).isEqualTo("spec-queue-runner");
    }

    @Test
    void keycloakAndOpaqueTokensGoToPrimary() {
        assertThat(decoder.decode("kc")).isSameAs(keycloakJwt);
        assertThatThrownBy(() -> decoder.decode("opaque")).hasMessage("primary rejected");
    }

    @Test
    void rejectsBadRunnerTokens() throws Exception {
        Instant now = Instant.now();
        assertThatThrownBy(() -> decoder.decode(sign(claims(ISSUER, now, now.plusSeconds(60)).build(),
                "another-secret-that-is-also-long-enough-xx"))).isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> decoder.decode(sign(claims(ISSUER, now.minusSeconds(300), now.minusSeconds(200)).build(), SECRET)))
                .isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> decoder.decode(sign(claims(ISSUER, now, now.plusSeconds(121)).build(), SECRET)))
                .isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> decoder.decode(sign(claims(ISSUER, null, now.plusSeconds(60)).build(), SECRET)))
                .isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> decoder.decode(sign(claims(ISSUER, now, null).build(), SECRET)))
                .isInstanceOf(BadJwtException.class);
        // forged iat in the future with far-future exp
        assertThatThrownBy(() -> decoder.decode(sign(claims(ISSUER, now.plusSeconds(3600), now.plusSeconds(3660)).build(), SECRET)))
                .isInstanceOf(BadJwtException.class);
        var wrongAud = new JWTClaimsSet.Builder().issuer(ISSUER).issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60))).audience("other").build();
        assertThatThrownBy(() -> decoder.decode(sign(wrongAud, SECRET))).isInstanceOf(BadJwtException.class);
        var noAud = new JWTClaimsSet.Builder().issuer(ISSUER).issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60))).build();
        assertThatThrownBy(() -> decoder.decode(sign(noAud, SECRET))).isInstanceOf(BadJwtException.class);
    }

    @Test
    void acceptsExactMaxLifetimeAndSmallExpiredSkew() throws Exception {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        assertThat(decoder.decode(sign(claims(ISSUER, now, now.plusSeconds(120)).build(), SECRET))).isNotNull();
        assertThat(decoder.decode(sign(claims(ISSUER, now.minusSeconds(100), now.minusSeconds(10)).build(), SECRET))).isNotNull();
    }

    @Test
    void nonRunnerShapesAreLeftToPrimary() throws Exception {
        Instant now = Instant.now();
        // HS256 with the Keycloak issuer
        assertThatThrownBy(() -> decoder.decode(sign(claims("http://localhost:8080/realms/openjcockpit", now, now.plusSeconds(60)).build(), SECRET)))
                .hasMessage("primary rejected");
        // unsigned token with the runner issuer
        assertThatThrownBy(() -> decoder.decode(new PlainJWT(claims(ISSUER, now, now.plusSeconds(60)).build()).serialize()))
                .hasMessage("primary rejected");
    }

    @Test
    void propertiesRejectShortSecretsMeasuredInBytes() {
        var p = new SpecQueueRunnerTokenProperties();
        p.setSecret("");
        p.validate();
        assertThat(p.isConfigured()).isFalse();
        p.setSecret("   ");
        assertThat(p.isConfigured()).isFalse();
        p.setSecret("x".repeat(31));
        assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
        p.setSecret("x".repeat(32));
        p.validate();
        p.setSecret("é".repeat(16)); // 16 chars, 32 bytes
        p.validate();
        p.setSecret("é".repeat(15)); // 30 bytes
        assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
    }
}
