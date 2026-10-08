package nl.metafactory.agents.policy;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.agents.policy.config.OpaProperties;
import nl.metafactory.agents.policy.model.OpaDecisionRequest;
import nl.metafactory.agents.policy.model.OpaDecisionResponse;
import nl.metafactory.agents.policy.model.PolicyDecisionContext;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpOpenPolicyAgentClientTest {

    private MockWebServer server;
    private OpaProperties properties;
    private HttpOpenPolicyAgentClient client;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        properties = new OpaProperties();
        properties.setBaseUrl(server.url("/").toString().replaceAll("/$", ""));
        properties.setPolicyPath("/v1/data/openjcockpit/workflow/decision");
        properties.setHealthPath("/health");
        properties.setTimeoutSeconds(2);
        client = new HttpOpenPolicyAgentClient(WebClient.builder(), properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private OpaDecisionRequest request() {
        var context = new PolicyDecisionContext("wf-1", "exec-1", null, null, null, null, "prod",
                "dashboard-button", null, null, null, null, null, null, null, null, "workflow.start",
                null, null, null, null, null, null, null, Instant.now(), Map.of());
        return new OpaDecisionRequest(context);
    }

    @Test
    void isReachableReturnsTrueOn2xx() {
        server.enqueue(new MockResponse().setResponseCode(200));

        assertThat(client.isReachable()).isTrue();
    }

    @Test
    void isReachableReturnsFalseOnErrorStatus() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThat(client.isReachable()).isFalse();
    }

    @Test
    void isReachableReturnsFalseOnConnectionError() throws IOException {
        server.shutdown();

        assertThat(client.isReachable()).isFalse();
    }

    @Test
    void evaluateReturnsParsedDecision() throws Exception {
        var response = new OpaDecisionResponse(
                new OpaDecisionResponse.OpaResult(false, "requires human approval", true, "high",
                        List.of("agent-tool-governance")),
                "4ca636c1-55e4-417a-b1d8-4aceb67960d1");
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(response))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var result = client.evaluate(request());

        assertThat(result.result().allowed()).isFalse();
        assertThat(result.result().reason()).isEqualTo("requires human approval");
        assertThat(result.result().requiredApproval()).isTrue();
        assertThat(result.result().riskLevel()).isEqualTo("high");
        assertThat(result.decisionId()).isEqualTo("4ca636c1-55e4-417a-b1d8-4aceb67960d1");
    }

    @Test
    void evaluatePostsToConfiguredPolicyPath() throws Exception {
        var response = new OpaDecisionResponse(
                new OpaDecisionResponse.OpaResult(true, "allowed", false, "low", List.of()), "decision-1");
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(response))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        client.evaluate(request());

        var recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/v1/data/openjcockpit/workflow/decision");
    }

    @Test
    void evaluateSendsBearerTokenWhenConfigured() throws Exception {
        properties.setAuthToken("test-token");
        var response = new OpaDecisionResponse(
                new OpaDecisionResponse.OpaResult(true, "allowed", false, "low", List.of()), "decision-1");
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(response))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        client.evaluate(request());

        var recorded = server.takeRequest();
        assertThat(recorded.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-token");
    }

    @Test
    void evaluateOmitsAuthorizationHeaderWhenTokenBlank() throws Exception {
        var response = new OpaDecisionResponse(
                new OpaDecisionResponse.OpaResult(true, "allowed", false, "low", List.of()), "decision-1");
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(response))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        client.evaluate(request());

        var recorded = server.takeRequest();
        assertThat(recorded.getHeader(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void evaluateThrowsOpaClientExceptionOnErrorStatus() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> client.evaluate(request()))
                .isInstanceOf(OpaClientException.class);
    }

    @Test
    void evaluateThrowsOpaClientExceptionOnConnectionError() throws IOException {
        server.shutdown();

        assertThatThrownBy(() -> client.evaluate(request()))
                .isInstanceOf(OpaClientException.class);
    }
}
