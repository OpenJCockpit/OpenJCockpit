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

/**
 * Mirrors {@link EmbabelAgentClientTest}'s MockWebServer harness (D2/B2), scoped to the three new
 * approval-gate relay methods. {@code ai-control-service} performs zero gate business logic — this
 * only proves the relay's success paths and its 400/404/409 error mapping.
 */
class EmbabelAgentClientApprovalGateTest {

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

    private ApprovalGateContextDto context() {
        return new ApprovalGateContextDto("run-1", "wf-1", "realisation", ApprovalStageOutcome.PUBLISHED,
                null, "Validation added", List.of("src/App.java"), 1, 0,
                "feat/wf-1-run", "https://github.com/org/repo/pull/9", null,
                1, 3, true, true, List.of(), Instant.now());
    }

    @Test
    void getApprovalGateContextReturnsContext() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(context()))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var result = client.getApprovalGateContext("run-1");

        assertThat(result).isPresent();
        assertThat(result.get().feedbackSupported()).isTrue();
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/agent-runs/run-1/approval-gate");
    }

    @Test
    void getApprovalGateContextReturnsEmptyOn404() {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThat(client.getApprovalGateContext("run-1")).isEmpty();
    }

    @Test
    void submitApprovalDecisionPostsRequestAndReturnsResult() throws Exception {
        var result = new ApprovalDecisionResultDto("run-1", "RUNNING", 1, "Accepted");
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(result))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var request = new ApprovalDecisionRequestDto(ApprovalDecisionKind.ACCEPT, 1, null);
        var response = client.submitApprovalDecision("run-1", request);

        assertThat(response.status()).isEqualTo("RUNNING");
        var recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/api/agent-runs/run-1/approval-gate/decision");
    }

    // D-QA-1 (workflow-approval-gate QA report §9): embabel-agent-service's real, deployed error
    // body is the standard Spring Boot shape below (confirmed live against a real running
    // container), not a bare plain-text string — a hand-crafted plain-text mock body would
    // exercise a shape the real server never produces (QA's "passes vacuously" finding).
    private String springBootErrorBody(int status, String error, String message, String path) {
        return "{\"timestamp\":\"2026-01-01T00:00:00.000+00:00\",\"status\":" + status
                + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\",\"path\":\"" + path + "\"}";
    }

    @Test
    void submitApprovalDecisionPropagates400AsResponseStatusException() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody(springBootErrorBody(400, "Bad Request", "Feedback is not supported for placement stage impact",
                        "/api/agent-runs/run-1/approval-gate/decision"))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var request = new ApprovalDecisionRequestDto(ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 1, "feedback");

        assertThatThrownBy(() -> client.submitApprovalDecision("run-1", request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400))
                .hasMessageContaining("Feedback is not supported")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("\"timestamp\""));
    }

    @Test
    void submitApprovalDecisionPropagates404AsResponseStatusException() {
        server.enqueue(new MockResponse().setResponseCode(404)
                .setBody(springBootErrorBody(404, "Not Found", "Run not found: missing",
                        "/api/agent-runs/missing/approval-gate/decision"))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var request = new ApprovalDecisionRequestDto(ApprovalDecisionKind.ACCEPT, 1, null);

        assertThatThrownBy(() -> client.submitApprovalDecision("missing", request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404))
                .hasMessageContaining("Run not found: missing")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("\"timestamp\""));
    }

    @Test
    void submitApprovalDecisionPropagates409AsResponseStatusException() {
        server.enqueue(new MockResponse().setResponseCode(409)
                .setBody(springBootErrorBody(409, "Conflict", "Decision already recorded for iteration 1",
                        "/api/agent-runs/run-1/approval-gate/decision"))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var request = new ApprovalDecisionRequestDto(ApprovalDecisionKind.ACCEPT, 1, null);

        assertThatThrownBy(() -> client.submitApprovalDecision("run-1", request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409))
                .hasMessageContaining("already recorded")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("\"timestamp\""));
    }

    @Test
    void listApprovalDecisionsReturnsAuditTrail() throws Exception {
        var entry = new ApprovalDecisionAuditEntryDto("d-1", "run-1", "wf-1", "realisation", 1,
                ApprovalDecisionKind.ACCEPT, null, "alice", "sub-1", ApprovalStageOutcome.PUBLISHED,
                "feat/wf-1-run", "https://github.com/org/repo/pull/9", Instant.now());
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(List.of(entry)))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var result = client.listApprovalDecisions("run-1");

        assertThat(result).extracting(ApprovalDecisionAuditEntryDto::id).containsExactly("d-1");
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/agent-runs/run-1/approval-decisions");
    }

    @Test
    void listApprovalDecisionsReturnsEmptyListWhenNoEntriesRecorded() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("[]")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThat(client.listApprovalDecisions("run-1")).isEmpty();
    }
}
