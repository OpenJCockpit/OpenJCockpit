package nl.metafactory.agents.a2a.client;

import nl.metafactory.common.agentdefinition.LiteLlmHttpClient;
import nl.metafactory.common.agentdefinition.SourceStatus;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Contract test against a response shaped by LiteLLM v1.101.2 {@code list[AgentResponse]}. */
class LiteLlmAgentMetadataClientTest {

    MockWebServer server;
    LiteLlmAgentMetadataClient client;

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new LiteLlmAgentMetadataClient(new LiteLlmHttpClient(server.url("/").toString(), "sk-test",
                Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    private void enqueueFixture() throws Exception {
        server.enqueue(new MockResponse().setBody(Files.readString(Path.of("src/test/resources/litellm/v1-agents-list.json"))));
    }

    @Test
    void readsAgentIdAndCapabilities() throws Exception {
        enqueueFixture();
        var result = client.findByName("customer-support");

        assertThat(result.status()).isEqualTo(SourceStatus.FOUND);
        var m = result.metadata();
        assertThat(m.litellmAgentId()).isEqualTo("3f1c2d4e-0000-4000-8000-000000000001");
        assertThat(m.protocolVersion()).isEqualTo("0.3.0");
        assertThat(m.url()).endsWith("/a2a/customer-support");
        assertThat(m.streaming()).isFalse();
        assertThat(m.skills()).extracting(AgentCardMetadata.CardSkill::id).containsExactly("customer-support");
        assertThat(server.takeRequest().getPath()).isEqualTo("/v1/agents");
    }

    @Test
    void metadataCarriesNoPromptContent() {
        var components = Arrays.stream(AgentCardMetadata.class.getRecordComponents()).map(RecordComponent::getName).toList();
        assertThat(components).doesNotContain("instructions", "markdown", "prompt", "systemPrompt");
    }

    @Test
    void unknownNameIsNotFound() throws Exception {
        enqueueFixture();
        assertThat(client.findByName("does-not-exist").status()).isEqualTo(SourceStatus.NOT_FOUND);
    }

    @Test
    void malformedPayloadsAreInvalid() {
        server.enqueue(new MockResponse().setBody("{\"agents\":[]}"));
        assertThat(client.findByName("x").status()).isEqualTo(SourceStatus.INVALID);
        server.enqueue(new MockResponse().setBody("[{\"agent_id\":\"1\",\"agent_name\":\"x\",\"agent_card_params\":{\"name\":\"x\"}}]"));
        assertThat(client.findByName("x").status()).isEqualTo(SourceStatus.INVALID);
        server.enqueue(new MockResponse().setBody("[{\"agent_id\":\"1\",\"agent_name\":\"x\"},{\"agent_id\":\"2\",\"agent_name\":\"x\"}]"));
        assertThat(client.findByName("x").status()).isEqualTo(SourceStatus.INVALID);
    }

    @Test
    void authFailuresPropagate() {
        server.enqueue(new MockResponse().setResponseCode(403));
        assertThat(client.findByName("x").status()).isEqualTo(SourceStatus.UNAUTHORIZED);
    }

    @Test
    void aListThatIsNoArrayIsInvalidAndMalformedAgentsAreSkipped() {
        server.enqueue(new MockResponse().setBody("{\"agents\":[]}"));
        assertThat(client.listAll().reason()).isEqualTo("agent list is not an array");

        server.enqueue(new MockResponse().setBody("""
                [{"agent_name":"no-id","agent_card_params":{}},
                 {"agent_id":"a1","agent_name":"skills-object","agent_card_params":{"url":"u","protocolVersion":"0.3.0","skills":{}}},
                 {"agent_id":"a2","agent_name":"skill-without-id","agent_card_params":{"url":"u","protocolVersion":"0.3.0","skills":[{"name":"x"}]}},
                 {"agent_id":"a3","agent_name":"ok","agent_card_params":{"url":"u","protocolVersion":"0.3.0","skills":[{"id":"s"}]}}]"""));
        var listed = client.listAll();

        assertThat(listed.agents()).extracting(AgentCardMetadata::agentName).containsExactly("ok");
        assertThat(listed.skippedInvalid()).isEqualTo(3);
    }

    @Test
    void theReasonForEachMalformedAgentIsSpecific() {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        String card = "\"agent_card_params\":{\"url\":\"u\",\"protocolVersion\":\"0.3.0\",";

        assertThat(LiteLlmAgentMetadataClient.parse(json.readTree("{\"agent_name\":\"x\"}")).reason())
                .isEqualTo("agent payload lacks agent_id, agent_name or agent_card_params");
        assertThat(LiteLlmAgentMetadataClient.parse(json.readTree("{\"agent_id\":\"a\",\"agent_name\":\"x\"," + card
                + "\"skills\":\"all\"}}")).reason()).isEqualTo("agent card skills is not an array");
        assertThat(LiteLlmAgentMetadataClient.parse(json.readTree("{\"agent_id\":\"a\",\"agent_name\":\"x\"," + card
                + "\"skills\":[{}]}}")).reason()).isEqualTo("agent card skill without id");
    }
}
