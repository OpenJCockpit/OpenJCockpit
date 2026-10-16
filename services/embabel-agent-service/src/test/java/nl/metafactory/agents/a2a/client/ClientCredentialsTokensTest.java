package nl.metafactory.agents.a2a.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ClientCredentialsTokensTest {

    MockWebServer server;
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-14T10:00:00Z"));
    final Clock clock = new Clock() {
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return now.get();
        }
    };

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    A2AProvidersProperties.Provider.CallerAuth auth() {
        var auth = new A2AProvidersProperties.Provider.CallerAuth();
        auth.setTokenUrl(server.url("/token").toString());
        auth.setClientId("openjcockpit-a2a");
        auth.setClientSecret("s3cret");
        return auth;
    }

    @Test
    void fetchesWithClientCredentialsAndCachesUntilShortlyBeforeExpiry() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"access_token\":\"t1\",\"expires_in\":300}"));
        server.enqueue(new MockResponse().setBody("{\"access_token\":\"t2\",\"expires_in\":300}"));
        var tokens = new ClientCredentialsTokens(auth(), clock);

        assertThat(tokens.authorization()).contains("Bearer t1");
        assertThat(tokens.authorization()).contains("Bearer t1");
        assertThat(server.takeRequest().getBody().readUtf8())
                .contains("grant_type=client_credentials", "client_id=openjcockpit-a2a", "client_secret=s3cret");

        now.set(now.get().plus(Duration.ofSeconds(280)));
        assertThat(tokens.authorization()).contains("Bearer t2");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void noCallerIdentityConfiguredMeansNoAuthorization() {
        assertThat(new ClientCredentialsTokens(new A2AProvidersProperties.Provider.CallerAuth(), clock).authorization()).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void secretNeverRendered() {
        assertThat(auth().toString()).doesNotContain("s3cret");
    }
}
