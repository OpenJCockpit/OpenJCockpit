package nl.metafactory.aicontrol.client;

import nl.metafactory.aicontrol.config.BearerTokenRelayFilter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbabelAgentClientStrictReadTest {

    private MockWebServer server;
    private EmbabelAgentClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new EmbabelAgentClient(WebClient.builder()
                .baseUrl(server.url("/").toString()).filter(new BearerTokenRelayFilter()).build());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private void json(String body) {
        server.enqueue(new MockResponse().setBody(body)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));
    }

    private static void assertBadGateway(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void workflowStrictReturnsWorkflowEmptyOn404AndBadGatewayOtherwise() throws Exception {
        json("{\"id\":\"wf-1\",\"name\":\"WF\",\"projectName\":\"p\",\"promptRequired\":false}");
        var found = client.getWorkflowStrict("wf-1");
        assertThat(found).isPresent();
        assertThat(found.get().projectName()).isEqualTo("p");
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/workflows/wf-1");

        server.enqueue(new MockResponse().setResponseCode(404));
        assertThat(client.getWorkflowStrict("missing")).isEmpty();

        server.enqueue(new MockResponse().setResponseCode(500));
        assertBadGateway(() -> client.getWorkflowStrict("wf-1"));

        server.enqueue(new MockResponse().setResponseCode(200));
        assertBadGateway(() -> client.getWorkflowStrict("wf-1"));

        server.shutdown();
        assertBadGateway(() -> client.getWorkflowStrict("wf-1"));
    }

    @Test
    void workflowGroupStrictReturnsGroupEmptyOn404AndBadGatewayOtherwise() throws Exception {
        json("{\"id\":\"g-1\",\"name\":\"G\",\"projectName\":\"p\"}");
        assertThat(client.getWorkflowGroupStrict("g-1")).isPresent();
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/workflow-groups/g-1");

        server.enqueue(new MockResponse().setResponseCode(404));
        assertThat(client.getWorkflowGroupStrict("missing")).isEmpty();

        server.enqueue(new MockResponse().setResponseCode(503));
        assertBadGateway(() -> client.getWorkflowGroupStrict("g-1"));

        server.enqueue(new MockResponse().setResponseCode(200));
        assertBadGateway(() -> client.getWorkflowGroupStrict("g-1"));
    }
}
