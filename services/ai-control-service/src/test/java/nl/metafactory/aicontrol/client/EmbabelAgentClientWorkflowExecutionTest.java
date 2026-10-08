package nl.metafactory.aicontrol.client;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.config.BearerTokenRelayFilter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbabelAgentClientWorkflowExecutionTest {

    private MockWebServer server;
    private EmbabelAgentClient client;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        var webClient = WebClient.builder()
                .baseUrl(server.url("/").toString())
                .filter(new BearerTokenRelayFilter())
                .build();
        client = new EmbabelAgentClient(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private WorkflowExecutionSummaryDto summary(String runId) {
        return new WorkflowExecutionSummaryDto(runId, "wf-1", "COMPLETED",
                Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T00:05:00Z"), 300000L, "alice");
    }

    @Test
    void listWorkflowExecutionsOrThrowReturnsPage() throws Exception {
        var page = new WorkflowExecutionPageDto(List.of(summary("run-1")), 20, 0, 1, false);
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(page))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var result = client.listWorkflowExecutionsOrThrow("wf-1", 20, 0);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).runId()).isEqualTo("run-1");
        assertThat(result.limit()).isEqualTo(20);
        assertThat(result.offset()).isEqualTo(0);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.hasMore()).isFalse();

        var recorded = server.takeRequest();
        assertThat(recorded.getPath()).startsWith("/api/workflows/wf-1/executions");
        assertThat(recorded.getPath()).contains("limit=20").contains("offset=0");
    }

    @Test
    void listWorkflowExecutionsOrThrowOmitsQueryParamsWhenNull() throws Exception {
        var page = new WorkflowExecutionPageDto(List.of(), 20, 0, 0, false);
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(page))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        client.listWorkflowExecutionsOrThrow("wf-1", null, null);

        var recorded = server.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/api/workflows/wf-1/executions");
    }

    @Test
    void listWorkflowExecutionsOrThrowPropagatesServerErrorInsteadOfSwallowingIt() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> client.listWorkflowExecutionsOrThrow("wf-1", 20, 0)).isNotNull();
    }

    @Test
    void listWorkflowExecutionsOrThrowPropagatesConnectionErrorInsteadOfSwallowingIt() throws IOException {
        server.shutdown();

        assertThatThrownBy(() -> client.listWorkflowExecutionsOrThrow("wf-1", 20, 0)).isNotNull();
    }

    @Test
    void getWorkflowExecutionOrThrowReturnsRun() throws Exception {
        var run = new AgentRunDto("run-1", "cust-1", "spec.md", "https://github.com/org/repo",
                "COMPLETED", Instant.parse("2024-01-01T00:00:00Z"), List.of(), List.of(),
                "wf-1", "alice", Instant.parse("2024-01-01T00:05:00Z"), null);
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(run))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var result = client.getWorkflowExecutionOrThrow("wf-1", "run-1");

        assertThat(result.runId()).isEqualTo("run-1");
        assertThat(result.workflowId()).isEqualTo("wf-1");
        assertThat(result.startedBy()).isEqualTo("alice");

        var recorded = server.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/api/workflows/wf-1/executions/run-1");
    }

    @Test
    void getWorkflowExecutionOrThrowThrowsNotFoundAsResponseStatusException() {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThatThrownBy(() -> client.getWorkflowExecutionOrThrow("wf-1", "missing-run"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void getWorkflowExecutionOrThrowPropagatesServerErrorInsteadOfSwallowingIt() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> client.getWorkflowExecutionOrThrow("wf-1", "run-1")).isNotNull();
    }

    @Test
    void getWorkflowExecutionOrThrowPropagatesConnectionErrorInsteadOfSwallowingIt() throws IOException {
        server.shutdown();

        assertThatThrownBy(() -> client.getWorkflowExecutionOrThrow("wf-1", "run-1")).isNotNull();
    }

    @Test
    void listWorkflowExecutionsOrThrowThrowsNotFoundAsResponseStatusException() {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThatThrownBy(() -> client.listWorkflowExecutionsOrThrow("wf-1", 20, 0))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404);
                    assertThat(((ResponseStatusException) e).getReason()).doesNotContain("http://");
                });
    }
}
