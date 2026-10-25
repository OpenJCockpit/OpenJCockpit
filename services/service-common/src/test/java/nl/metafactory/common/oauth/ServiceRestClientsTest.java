package nl.metafactory.common.oauth;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceRestClientsTest {

    @Test
    void everyRequestCarriesTheServiceIdentityAndRedirectsAreNotFollowed() throws Exception {
        try (var server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("ok"));
            server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", "http://elsewhere/"));
            var tokens = mock(ClientCredentialsTokens.class);
            when(tokens.authorization()).thenReturn(Optional.of("Bearer svc-token"));
            var client = ServiceRestClients.create(server.url("/base/").toString(), tokens, Duration.ofSeconds(2),
                    Duration.ofSeconds(2));

            assertThat(client.get().uri("/ping").retrieve().body(String.class)).isEqualTo("ok");
            var request = server.takeRequest();
            assertThat(request.getPath()).isEqualTo("/base/ping");
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer svc-token");

            assertThat(client.get().uri("/moved").retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(302);
        }
    }

    @Test
    void withoutAServiceIdentityNothingIsSent() throws Exception {
        try (var server = new MockWebServer()) {
            var tokens = mock(ClientCredentialsTokens.class);
            when(tokens.authorization()).thenReturn(Optional.empty());
            var client = ServiceRestClients.create(server.url("/").toString(), tokens, Duration.ofSeconds(1),
                    Duration.ofSeconds(1));

            assertThatThrownBy(() -> client.get().uri("/x").retrieve().body(String.class))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(server.getRequestCount()).isZero();
        }
    }

    @Test
    void aReadTimeoutIsReportedAsAnAccessFailure() throws Exception {
        try (var server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("late").setHeadersDelay(2, java.util.concurrent.TimeUnit.SECONDS));
            var tokens = mock(ClientCredentialsTokens.class);
            when(tokens.authorization()).thenReturn(Optional.of("Bearer t"));
            var client = ServiceRestClients.create(server.url("/").toString(), tokens, Duration.ofSeconds(1),
                    Duration.ofMillis(200));

            assertThatThrownBy(() -> client.get().uri("/slow").retrieve().body(String.class))
                    .isInstanceOf(ResourceAccessException.class);
        }
    }

    @Test
    void baseUrlsMustBeAbsoluteHttpUrlsAndLoseTrailingSlashes() {
        assertThat(ServiceRestClients.normalizeBaseUrl(" https://runtime.example.com/agents// "))
                .isEqualTo("https://runtime.example.com/agents");
        assertThat(ServiceRestClients.normalizeBaseUrl("http://embabel-agent-runtime:8082"))
                .isEqualTo("http://embabel-agent-runtime:8082");
        for (String bad : new String[]{null, " ", "ftp://host", "runtime:8082", "/relative", "http:///nohost"}) {
            assertThatThrownBy(() -> ServiceRestClients.normalizeBaseUrl(bad))
                    .as(String.valueOf(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
