package nl.metafactory.agents.config;

import nl.metafactory.agents.security.SpecQueueRunnerTokenProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression: only the runner issuer may ever receive the SPEC_QUEUE_RUNNER authority. */
class SecurityConfigAuthorityTest {

    private static final String RUNNER_ISSUER = "urn:openjcockpit:ai-control-service:spec-queue-runner";
    private static final String KEYCLOAK_ISSUER = "https://keycloak.example/realms/openjcockpit";

    private static SpecQueueRunnerTokenProperties configuredProps() {
        var props = new SpecQueueRunnerTokenProperties();
        props.setSecret("test-only-spec-queue-runner-secret-32-bytes-minimum");
        return props;
    }

    private static Jwt jwt(String issuer, Map<String, Object> extraClaims) {
        return Jwt.withTokenValue("t").header("alg", "none").issuer(issuer).subject("someone")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claims(c -> c.putAll(extraClaims)).build();
    }

    private static List<String> authorities(SpecQueueRunnerTokenProperties props, Jwt jwt) {
        return SecurityConfig.jwtAuthenticationConverter(props).convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void runnerIssuerGetsOnlyTheRunnerAuthority() {
        assertThat(authorities(configuredProps(), jwt(RUNNER_ISSUER, Map.of())))
                .containsExactly(SecurityConfig.SPEC_QUEUE_RUNNER);
    }

    @Test
    void keycloakTokenWithRunnerScopeDoesNotGetRunnerAuthority() {
        assertThat(authorities(configuredProps(),
                jwt(KEYCLOAK_ISSUER, Map.of("scope", SecurityConfig.SPEC_QUEUE_RUNNER))))
                .doesNotContain(SecurityConfig.SPEC_QUEUE_RUNNER);
    }

    @Test
    void keycloakTokenWithRunnerRoleDoesNotGetRunnerAuthority() {
        Jwt token = jwt(KEYCLOAK_ISSUER, Map.of(
                "realm_access", Map.of("roles", List.of(SecurityConfig.SPEC_QUEUE_RUNNER)),
                "roles", List.of(SecurityConfig.SPEC_QUEUE_RUNNER),
                "authorities", List.of(SecurityConfig.SPEC_QUEUE_RUNNER)));

        assertThat(authorities(configuredProps(), token))
                .doesNotContain(SecurityConfig.SPEC_QUEUE_RUNNER, "ROLE_" + SecurityConfig.SPEC_QUEUE_RUNNER);
    }

    @Test
    void runnerIssuerIsNotPrivilegedWhenRunnerIsNotConfigured() {
        assertThat(authorities(new SpecQueueRunnerTokenProperties(), jwt(RUNNER_ISSUER, Map.of())))
                .doesNotContain(SecurityConfig.SPEC_QUEUE_RUNNER);
    }
}
