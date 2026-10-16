package nl.metafactory.agents.definition.litellm;

import nl.metafactory.agents.definition.model.SourceStatus;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiteLlmHttpClientTest {

    MockWebServer server;
    LiteLlmHttpClient client;

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new LiteLlmHttpClient(server.url("/").toString(), "sk-test-key", Duration.ofSeconds(1),
                Duration.ofMillis(300));
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    @Test
    void successSendsBearerAndParsesJson() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"agent_id\":\"a1\"}").addHeader("Content-Type", "application/json"));
        var r = client.get("/v1/agents/{id}", "a1");
        assertThat(r.status()).isEqualTo(SourceStatus.FOUND);
        assertThat(r.body().get("agent_id").asString()).isEqualTo("a1");
        var request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer sk-test-key");
        assertThat(request.getPath()).isEqualTo("/v1/agents/a1");
    }

    @Test
    void pathVariablesAreEncodedNotInterpreted() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));
        client.get("/v1/agents/{id}", "../key/info");
        assertThat(server.takeRequest(1, TimeUnit.SECONDS).getPath()).isEqualTo("/v1/agents/..%2Fkey%2Finfo");
    }

    @Test
    void classifiesAuthFailures() {
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setResponseCode(403));
        assertThat(client.get("/v1/agents").status()).isEqualTo(SourceStatus.UNAUTHORIZED);
        assertThat(client.get("/v1/agents").status()).isEqualTo(SourceStatus.UNAUTHORIZED);
    }

    @Test
    void classifiesNotFoundServerErrorAndGarbage() {
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setBody("<html>not json"));
        assertThat(client.get("/v1/agents/x").status()).isEqualTo(SourceStatus.NOT_FOUND);
        assertThat(client.get("/v1/agents/x").status()).isEqualTo(SourceStatus.UNAVAILABLE);
        assertThat(client.get("/v1/agents/x").status()).isEqualTo(SourceStatus.INVALID);
    }

    @Test
    void readTimeoutIsUnavailable() {
        server.enqueue(new MockResponse().setBody("{}").setHeadersDelay(2, TimeUnit.SECONDS));
        long start = System.nanoTime();
        assertThat(client.get("/v1/agents").status()).isEqualTo(SourceStatus.UNAVAILABLE);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void connectionDropIsUnavailable() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        assertThat(client.get("/v1/agents").status()).isEqualTo(SourceStatus.UNAVAILABLE);
    }

    @Test
    void redirectsAreNotFollowed() {
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "http://evil.example/"));
        assertThat(client.get("/v1/agents").status()).isNotEqualTo(SourceStatus.FOUND);
    }

    @Test
    void apiKeyIsMandatory() {
        assertThatThrownBy(() -> new LiteLlmHttpClient("http://x", " ", Duration.ofSeconds(1), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
