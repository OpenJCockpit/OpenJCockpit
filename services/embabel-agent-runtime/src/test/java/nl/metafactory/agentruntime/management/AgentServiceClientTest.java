package nl.metafactory.agentruntime.management;

import nl.metafactory.common.agentruntime.ToolInvocationDecision;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The runtime reads management data from the agent service; anything but an answer or a 404 fails closed. */
class AgentServiceClientTest {

    private MockWebServer server;
    private AgentServiceClient client;

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new AgentServiceClient(RestClient.builder().baseUrl(server.url("/").toString()).build());
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    private void json(int status, String body) {
        server.enqueue(new MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body));
    }

    @Test
    void readsATenantBindingAndTreats404AsAbsent() throws Exception {
        json(200, """
                {"tenantId":"local","agentId":"qa-engineer","enabled":true,"modelAlias":"agent-chat","pinnedVersion":null,
                 "fallbackAllowed":false,"fallbackOnNotFound":false,"allowedTools":["git.read_file"],
                 "requiredGuardrails":["pii-protection"]}""");
        json(404, "");

        var binding = client.tenantBinding("local", "qa-engineer").orElseThrow();
        assertThat(binding.allowedTools()).containsExactly("git.read_file");
        assertThat(binding.requiredGuardrails()).containsExactly("pii-protection");
        assertThat(server.takeRequest(1, TimeUnit.SECONDS).getPath())
                .isEqualTo("/internal/agent-runtime/v1/tenants/local/agent-bindings/qa-engineer");

        assertThat(client.tenantBinding("other", "qa-engineer")).isEmpty();
    }

    @Test
    void readsAWorkflowRun() throws Exception {
        json(200, """
                {"workflowId":"wf-1","workflowRunId":"run-1","gitWorkspace":{"workspaceKey":"job-1",
                 "repositoryUrl":"https://git.example/r.git","branch":"b"}}""");

        var run = client.workflowRun("run-1").orElseThrow();

        assertThat(run.gitWorkspace().workspaceKey()).isEqualTo("job-1");
        assertThat(server.takeRequest(1, TimeUnit.SECONDS).getPath()).isEqualTo("/internal/agent-runtime/v1/workflow-runs/run-1");
    }

    @Test
    void postsToolDecisionRequests() throws Exception {
        json(200, "{\"allowed\":false,\"reason\":\"no workflow\"}");

        var decision = client.decideToolInvocation(new ToolInvocationDecision.Request(null, "agent-run", "local",
                "qa-engineer", "git.read_file", "alice", Map.of("traceId", "t")));

        assertThat(decision.allowed()).isFalse();
        var request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getBody().readUtf8()).contains("\"toolId\":\"git.read_file\"", "\"traceId\":\"t\"");
    }

    @Test
    void errorsEmptyAnswersAndAnUnconfiguredServiceAreUnavailable() {
        json(403, "");
        assertThatThrownBy(() -> client.tenantBinding("local", "a"))
                .isInstanceOf(AgentServiceClient.ManagementUnavailableException.class).hasMessageContaining("tenant-binding");
        json(500, "");
        assertThatThrownBy(() -> client.workflowRun("run-1"))
                .isInstanceOf(AgentServiceClient.ManagementUnavailableException.class);
        server.enqueue(new MockResponse().setResponseCode(200));
        assertThatThrownBy(() -> client.decideToolInvocation(new ToolInvocationDecision.Request(null, "r", "t", "a",
                "x", "p", null))).hasMessageContaining("empty response");

        var unconfigured = new AgentServiceClient(new AgentServiceProperties());
        assertThat(unconfigured.configured()).isFalse();
        assertThatThrownBy(() -> unconfigured.workflowRun("run-1"))
                .isInstanceOf(AgentServiceClient.ManagementUnavailableException.class).hasMessageContaining("not configured");
    }

    @Test
    void aConfiguredBaseUrlBuildsAnAuthenticatedClient() {
        var properties = new AgentServiceProperties();
        properties.setBaseUrl("https://agent-service.example.com");
        properties.setTokenUrl("https://keycloak.example.com/token");
        properties.setClientId("rt");
        properties.setClientSecret("s3cret");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(2));

        assertThat(new AgentServiceClient(properties).configured()).isTrue();
        assertThat(properties.getTokenUrl()).isEqualTo("https://keycloak.example.com/token");
        assertThat(properties.getClientId()).isEqualTo("rt");
        assertThat(properties.getClientSecret()).isEqualTo("s3cret");
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.toString()).contains("agent-service.example.com", "rt").doesNotContain("s3cret");

        var blank = new AgentServiceProperties();
        blank.setBaseUrl(" ");
        assertThat(new AgentServiceClient(blank).configured()).isFalse();
    }
}
