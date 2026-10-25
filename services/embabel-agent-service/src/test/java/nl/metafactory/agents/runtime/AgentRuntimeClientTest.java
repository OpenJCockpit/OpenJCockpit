package nl.metafactory.agents.runtime;

import nl.metafactory.agents.workflow.model.AgentSpec;
import nl.metafactory.common.agentruntime.AgentExecutionRequest;
import nl.metafactory.common.agentruntime.AgentExecutionStatus;
import nl.metafactory.common.agentruntime.DefinitionDrafts;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The service's side of the execution boundary: one request per execution, failures mapped, never retried. */
class AgentRuntimeClientTest {

    private MockWebServer server;
    private AgentRuntimeClient client;

    private final AgentExecutionRequest request =
            new AgentExecutionRequest("run-1:e-1", "local", "triage", "alice", "run-1", null, "msg", null);

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        server.start();
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofMillis(500));
        var rest = RestClient.builder().baseUrl(server.url("/").toString()).requestFactory(factory).build();
        client = new AgentRuntimeClient(rest, rest);
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    private void json(int status, String body) {
        server.enqueue(new MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body));
    }

    private AgentRuntimeClient.AgentRuntimeException failure(Runnable call) {
        try {
            call.run();
        } catch (AgentRuntimeClient.AgentRuntimeException e) {
            return e;
        }
        throw new AssertionError("expected a failure");
    }

    @Test
    void anExecutionReturnsTheAgentsAnswer() throws Exception {
        json(200, """
                {"executionId":"run-1:e-1","agentId":"triage","version":"3","contentHash":"h","traceId":"t","text":"done"}""");

        assertThat(client.execute(request).text()).isEqualTo("done");

        var sent = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(sent.getPath()).isEqualTo("/internal/v1/agent-executions");
        assertThat(sent.getBody().readUtf8()).contains("\"executionId\":\"run-1:e-1\"", "\"workflowRunId\":\"run-1\"");
    }

    @Test
    void theRuntimesRefusalKeepsItsReasonAndSafeMessage() {
        json(422, "{\"executionId\":\"run-1:e-1\",\"reason\":\"TENANT_FORBIDDEN\",\"message\":\"Agent triage not runnable: TENANT_FORBIDDEN\"}");

        var e = failure(() -> client.execute(request));

        assertThat(e.reason()).isEqualTo("TENANT_FORBIDDEN");
        assertThat(e.getMessage()).isEqualTo("Agent triage not runnable: TENANT_FORBIDDEN");
    }

    @Test
    void anAnswerThatIsNotTheRuntimesIsAGenericUnavailability() {
        server.enqueue(new MockResponse().setResponseCode(502).setBody("<html>bad gateway</html>"));
        json(500, "{\"reason\":\"X\"}");
        json(200, "");

        var proxy = failure(() -> client.execute(request));
        assertThat(proxy.reason()).isEqualTo(AgentRuntimeClient.RUNTIME_UNAVAILABLE);
        assertThat(proxy.getMessage()).isEqualTo("The agent runtime answered HTTP 502");
        assertThat(failure(() -> client.execute(request)).getMessage()).contains("HTTP 500");
        assertThat(failure(() -> client.execute(request)).getMessage()).contains("returned no body");
    }

    @Test
    void aTimedOutExecutionIsNeverResentAndReportsWhereItStands() throws Exception {
        server.enqueue(new MockResponse().setBody("{}").setHeadersDelay(2, TimeUnit.SECONDS));
        json(200, "{\"executionId\":\"run-1:e-1\",\"state\":\"RUNNING\",\"startedAt\":\"2026-10-25T10:00:00Z\"}");

        var e = failure(() -> client.execute(request));

        assertThat(e.reason()).isEqualTo(AgentRuntimeClient.RUNTIME_UNAVAILABLE);
        assertThat(e.getMessage()).contains("run-1:e-1 is RUNNING there; it is not retried");
        assertThat(server.takeRequest(1, TimeUnit.SECONDS).getMethod()).isEqualTo("POST");
        assertThat(server.takeRequest(1, TimeUnit.SECONDS).getPath()).isEqualTo("/internal/v1/agent-executions/run-1%3Ae-1");
        assertThat(server.getRequestCount()).as("exactly one execution request").isEqualTo(2);
    }

    @Test
    void anUnreachableRuntimeLeavesTheStateUnknown() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        var e = failure(() -> client.execute(request));

        assertThat(e.getMessage()).contains("is UNKNOWN there");
    }

    @Test
    void statusIsEmptyForAnUnknownExecution() {
        json(404, "{\"reason\":\"UNKNOWN_EXECUTION\",\"message\":\"Unknown execution\"}");
        json(200, "{\"executionId\":\"e\",\"state\":\"COMPLETED\"}");

        assertThat(client.status("e")).isEmpty();
        assertThat(client.status("e")).get().extracting(AgentExecutionStatus::state)
                .isEqualTo(AgentExecutionStatus.State.COMPLETED);
    }

    @Test
    void draftsAreReadIntoTheDesignersModel() throws Exception {
        json(200, "{\"name\":\"triage\",\"role\":\"lead\",\"skillNames\":[\"classify\"],\"builtIn\":false}");

        AgentSpec spec = client.draft(DefinitionDrafts.Kind.AGENT, "triage issues", AgentSpec.class);

        assertThat(spec.name()).isEqualTo("triage");
        assertThat(spec.skillNames()).containsExactly("classify");
        var sent = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(sent.getPath()).isEqualTo("/internal/v1/definition-drafts/agent");
        assertThat(sent.getBody().readUtf8()).isEqualTo("{\"concept\":\"triage issues\"}");
    }

    @Test
    void anUnconfiguredRuntimeFailsEveryCallClearly() {
        var unconfigured = AgentRuntimeClient.create(new AgentRuntimeProperties());

        assertThat(unconfigured.configured()).isFalse();
        assertThat(unconfigured.status("e")).isEmpty();
        assertThat(failure(() -> unconfigured.execute(request)).getMessage())
                .contains("openjcockpit.agent-runtime.base-url");
        assertThat(failure(() -> unconfigured.draft(DefinitionDrafts.Kind.SKILL, "x", Object.class)).reason())
                .isEqualTo(AgentRuntimeClient.RUNTIME_UNAVAILABLE);
    }

    @Test
    void aConfiguredRuntimeCanBeAnyHttpsHost() {
        var properties = new AgentRuntimeProperties();
        properties.setBaseUrl("https://agents.example.com/runtime/");
        properties.setTokenUrl("https://keycloak.example.com/token");
        properties.setClientId("svc");
        properties.setClientSecret("s3cret");
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setExecutionTimeout(Duration.ofMinutes(30));
        properties.setDraftTimeout(Duration.ofMinutes(5));
        properties.setRuntimeClientId("rt");

        assertThat(AgentRuntimeClient.create(properties).configured()).isTrue();
        assertThat(properties.getBaseUrl()).isEqualTo("https://agents.example.com/runtime/");
        assertThat(properties.getTokenUrl()).isEqualTo("https://keycloak.example.com/token");
        assertThat(properties.getClientId()).isEqualTo("svc");
        assertThat(properties.getClientSecret()).isEqualTo("s3cret");
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getExecutionTimeout()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.getDraftTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.getRuntimeClientId()).isEqualTo("rt");
        assertThat(properties.toString()).contains("agents.example.com").doesNotContain("s3cret");

        var blank = new AgentRuntimeProperties();
        blank.setBaseUrl("  ");
        assertThat(AgentRuntimeClient.create(blank).configured()).isFalse();
        assertThatThrownBy(() -> {
            var bad = new AgentRuntimeProperties();
            bad.setBaseUrl("embabel-agent-runtime:8082");
            AgentRuntimeClient.create(bad);
        }).isInstanceOf(IllegalArgumentException.class);
    }
}
