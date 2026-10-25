package nl.metafactory.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceCallersTest {

    private static Jwt token(String azp) {
        var builder = Jwt.withTokenValue("t").header("alg", "RS256").subject("s")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (azp != null) {
            builder.claim("azp", azp);
        }
        return builder.build();
    }

    @Test
    void onlyTheConfiguredClientIsTheService() {
        assertThat(ServiceCallers.is(token("openjcockpit-agent-service"), "openjcockpit-agent-service")).isTrue();
        assertThat(ServiceCallers.is(token("dashboard"), "openjcockpit-agent-service")).isFalse();
        assertThat(ServiceCallers.is(token(null), "openjcockpit-agent-service")).isFalse();
        assertThat(ServiceCallers.is(null, "openjcockpit-agent-service")).isFalse();
    }

    @Test
    void anUnconfiguredClientIdNeverMatches() {
        assertThat(ServiceCallers.is(token(""), "")).isFalse();
        assertThat(ServiceCallers.is(token("x"), " ")).isFalse();
        assertThat(ServiceCallers.is(token("x"), null)).isFalse();
    }

    @Test
    void theClientIdIsAvailableForLogging() {
        assertThat(ServiceCallers.clientId(token("svc"))).isEqualTo("svc");
        assertThat(ServiceCallers.clientId(null)).isNull();
    }
}
