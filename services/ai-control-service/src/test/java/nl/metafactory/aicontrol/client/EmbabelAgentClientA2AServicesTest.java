package nl.metafactory.aicontrol.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbabelAgentClientA2AServicesTest {

    private MockWebServer server;
    private EmbabelAgentClient client;

    private static final String SERVICE = """
            {"id":"acme","name":"Acme","type":"litellm","source":"DASHBOARD","editable":true,
             "baseUrl":"https://gw.example","hasApiKey":true,"hasCallerClientSecret":false,
             "status":"OK","agentCount":3,"updatedAt":"2026-10-16T10:00:00Z"}""";

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new EmbabelAgentClient(WebClient.builder().baseUrl(server.url("/").toString()).build());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private void respond(int status, String body) {
        server.enqueue(new MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body));
    }

    @Test
    void listsCreatesUpdatesAndDeletesThroughTheAgentService() throws Exception {
        respond(200, "[" + SERVICE + "]");
        assertThat(client.listA2AServices()).singleElement().satisfies(s -> {
            assertThat(s.id()).isEqualTo("acme");
            assertThat(s.agentCount()).isEqualTo(3);
        });
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/a2a-services");

        var request = new A2AServiceRequest("acme", "Acme", "https://gw.example", null, "sk-secret", null, null, null);
        respond(201, SERVICE);
        assertThat(client.createA2AService(request).editable()).isTrue();
        var post = server.takeRequest();
        assertThat(post.getMethod()).isEqualTo("POST");
        assertThat(post.getBody().readUtf8()).contains("\"apiKey\":\"sk-secret\"");

        respond(200, SERVICE);
        client.updateA2AService("acme", request);
        var put = server.takeRequest();
        assertThat(put.getMethod() + " " + put.getPath()).isEqualTo("PUT /api/a2a-services/acme");

        server.enqueue(new MockResponse().setResponseCode(204));
        client.deleteA2AService("acme");
        assertThat(server.takeRequest().getMethod()).isEqualTo("DELETE");
        assertThat(request.toString()).doesNotContain("sk-secret");
    }

    @Test
    void validationConflictAndMissingKeyAnswersKeepTheirStatusAndMessage() {
        for (int code : new int[] {400, 404, 409, 503}) {
            respond(code, "{\"status\":" + code + ",\"message\":\"reason " + code + "\"}");
            assertThatThrownBy(() -> client.deleteA2AService("acme"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                        assertThat(e.getStatusCode().value()).isEqualTo(code);
                        assertThat(e.getReason()).isEqualTo("reason " + code);
                    });
        }
        respond(500, "{\"message\":\"internal detail\"}");
        assertThatThrownBy(() -> client.createA2AService(new A2AServiceRequest("a", "b", "c", null, null, null, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getReason()).doesNotContain("internal detail");
                });
    }

    @Test
    void anUnreachableAgentServiceIsABadGateway() throws IOException {
        server.shutdown();
        assertThatThrownBy(() -> client.listA2AServices())
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
