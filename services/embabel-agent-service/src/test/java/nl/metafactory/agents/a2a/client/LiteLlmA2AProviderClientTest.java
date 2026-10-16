package nl.metafactory.agents.a2a.client;

import nl.metafactory.agents.a2a.client.A2AProviderClient.Status;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Contract tests against LiteLLM v1.101.2-shaped responses; one client covers list, card and message/send. */
class LiteLlmA2AProviderClientTest {

    MockWebServer server;
    LiteLlmA2AProviderClient client;

    static A2AProvidersProperties.Provider config(String baseUrl, String key) {
        var p = new A2AProvidersProperties.Provider();
        p.setType("litellm");
        p.setName("LiteLLM");
        p.setBaseUrl(baseUrl);
        p.setPublicUrl("http://localhost:4100/");
        p.setApiKey(key);
        return p;
    }

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new LiteLlmA2AProviderClient(config(server.url("/").toString(), "sk-a2a"));
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    @Test
    void listsRegistryAgentsWithPublicGatewayUrls() throws Exception {
        server.enqueue(new MockResponse().setBody(Files.readString(Path.of("src/test/resources/litellm/v1-agents-list.json"))));
        var listing = client.listAgents();

        assertThat(listing.status()).isEqualTo(Status.OK);
        assertThat(listing.agents()).extracting(A2AProviderClient.Agent::name).containsExactly("customer-support", "billing");
        var first = listing.agents().getFirst();
        assertThat(first.url()).isEqualTo("http://localhost:4100/a2a/customer-support");
        assertThat(first.cardUrl()).isEqualTo("http://localhost:4100/a2a/customer-support/.well-known/agent-card.json");
        assertThat(first.backendUrl()).isEqualTo("http://embabel-agent-service:8081/a2a/customer-support");
        assertThat(first.skills()).containsExactly("Customer support");
        var request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/agents");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer sk-a2a");
    }

    @Test
    void incompleteCardsAreSkippedAndFailuresClassified() {
        server.enqueue(new MockResponse().setBody("[{\"agent_id\":\"1\",\"agent_name\":\"x\",\"agent_card_params\":{}}]"));
        assertThat(client.listAgents().skippedInvalid()).isEqualTo(1);
        server.enqueue(new MockResponse().setResponseCode(401));
        assertThat(client.listAgents().status()).isEqualTo(Status.UNAUTHORIZED);
        server.enqueue(new MockResponse().setResponseCode(503));
        assertThat(client.listAgents().status()).isEqualTo(Status.UNAVAILABLE);
    }

    @Test
    void fetchesTheHostedCardAndRewritesItsUrl() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"protocolVersion":"0.3.0","name":"qa-engineer","url":"http://litellm:4000/a2a/qa-engineer",
                 "supportedInterfaces":[{"url":"http://litellm:4000/a2a/qa-engineer"}]}"""));
        var card = client.agentCard("qa-engineer");

        assertThat(card.status()).isEqualTo(Status.OK);
        assertThat(card.card().get("url").asString()).isEqualTo("http://localhost:4100/a2a/qa-engineer");
        assertThat(card.card().get("supportedInterfaces").get(0).get("url").asString())
                .isEqualTo("http://localhost:4100/a2a/qa-engineer");
        assertThat(server.takeRequest().getPath()).isEqualTo("/a2a/qa-engineer/.well-known/agent-card.json");
        assertThat(client.agentCard("../key/info").status()).isEqualTo(Status.NOT_FOUND);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void sendsMessageAndForwardsTheCallerJwtTheLiteLlmWay() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"jsonrpc":"2.0","id":"1","result":{"kind":"message","role":"agent","messageId":"m",
                 "parts":[{"kind":"text","text":"REQ-001 Reset password"}]}}"""));
        var result = client.sendMessage("business-analyst", "Write requirements", "Bearer jwt-123");

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.text()).isEqualTo("REQ-001 Reset password");
        var request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/a2a/business-analyst");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer sk-a2a");
        assertThat(request.getHeader("x-a2a-business-analyst-authorization")).isEqualTo("Bearer jwt-123");
        assertThat(request.getBody().readUtf8()).contains("\"method\":\"message/send\"", "\"role\":\"user\"",
                "\"text\":\"Write requirements\"");
    }

    @Test
    void forwardsHeadersToTheAgentTheLiteLlmWayAndRejectsUnsafeOnes() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"jsonrpc":"2.0","id":"1","result":{"kind":"message","role":"agent","messageId":"m",
                 "parts":[{"kind":"text","text":"ok"}]}}"""));
        var result = client.sendMessage("software-developer", "go", null,
                java.util.Map.of("x-openjcockpit-workflow-run", "run-7"));

        assertThat(result.status()).isEqualTo(Status.OK);
        var request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getHeader("x-a2a-software-developer-x-openjcockpit-workflow-run")).isEqualTo("run-7");
        assertThat(client.sendMessage("software-developer", "go", null, java.util.Map.of("authorization", "x")).status())
                .isEqualTo(Status.INVALID);
        assertThat(client.sendMessage("software-developer", "go", null, java.util.Map.of("Bad Header", "x")).status())
                .isEqualTo(Status.INVALID);
    }

    @Test
    void readsTaskArtifactsAndReportsJsonRpcErrors() {
        server.enqueue(new MockResponse().setBody("""
                {"jsonrpc":"2.0","id":"1","result":{"kind":"task","id":"t","status":{"state":"completed"},
                 "artifacts":[{"artifactId":"a","parts":[{"kind":"text","text":"from task"}]}]}}"""));
        assertThat(client.sendMessage("qa-engineer", "hi", null).text()).isEqualTo("from task");

        server.enqueue(new MockResponse().setBody("""
                {"jsonrpc":"2.0","id":"1","error":{"code":-32600,"message":"Request not permitted","data":{"reason":"TENANT_FORBIDDEN"}}}"""));
        var denied = client.sendMessage("qa-engineer", "hi", null);
        assertThat(denied.status()).isEqualTo(Status.INVALID);
        assertThat(denied.error()).contains("-32600", "TENANT_FORBIDDEN");
    }

    @Test
    void unconfiguredProviderNeverCallsOut() {
        var unconfigured = new LiteLlmA2AProviderClient(config(null, null));
        assertThat(unconfigured.configured()).isFalse();
        assertThat(unconfigured.listAgents().status()).isEqualTo(Status.NOT_CONFIGURED);
        assertThat(unconfigured.agentCard("qa-engineer").status()).isEqualTo(Status.NOT_CONFIGURED);
        assertThat(unconfigured.sendMessage("qa-engineer", "hi", null).status()).isEqualTo(Status.NOT_CONFIGURED);
        assertThat(unconfigured.http()).isEmpty();
    }
}
