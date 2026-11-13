package nl.metafactory.agents.ops;

import nl.metafactory.common.agentdefinition.AgentMarkdownParser;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the LiteLLM agent registration in lock-step with what embabel-agent-runtime actually serves: the A2A
 * protocol it implements and its seeded delivery agents (both read from the OpenJCockpit-Agent-Runtime checkout).
 */
class LiteLlmRegistrationConfigTest {

    static final Path LITELLM = Path.of("../../infrastructure/litellm");
    static final String PROTOCOL_VERSION = "0.3.0";
    static final String TRANSPORT = "JSONRPC";
    static final List<String> DELIVERY_AGENTS =
            List.of("business-analyst", "solution-architect", "software-developer", "qa-engineer");

    static JsonNode config() throws IOException {
        return new YAMLMapper().readTree(LITELLM.resolve("config.yaml").toFile());
    }

    static JsonNode customerSupportRegistration() throws IOException {
        return new YAMLMapper().readTree(LITELLM.resolve("a2a/customer-support.agent.yaml").toFile()).get("agents").get(0);
    }

    static JsonNode registration(JsonNode config, String id) {
        for (JsonNode agent : config.get("agents")) {
            if (id.equals(agent.get("agent_name").asString())) {
                return agent;
            }
        }
        return null;
    }

    static String runtimeConstant(String source, String name) {
        var matcher = Pattern.compile(name + "\\s*=\\s*\"([^\"]+)\"").matcher(source);
        assertThat(matcher.find()).as(name + " in A2AProtocol").isTrue();
        return matcher.group(1);
    }

    @Test
    void registrationPinsTheProtocolVersion() throws IOException {
        JsonNode agent = customerSupportRegistration();
        JsonNode card = agent.get("agent_card_params");
        assertThat(agent.get("agent_name").asString()).isEqualTo("customer-support");
        assertThat(card.get("protocolVersion").asString()).isEqualTo(PROTOCOL_VERSION);
        assertThat(card.get("preferredTransport").asString()).isEqualTo(TRANSPORT);
        assertThat(card.get("url").asString()).endsWith("/a2a/customer-support");
        assertThat(card.get("capabilities").get("streaming").asBoolean()).isFalse();
        assertThat(card.get("capabilities").get("pushNotifications").asBoolean()).isFalse();
        assertThat(agent.get("litellm_params").get("guardrails").get(0).asString()).isEqualTo("openjcockpit-pii");
    }

    @Test
    void seededDeliveryAgentsAreRegisteredAtTheRuntime() throws IOException {
        JsonNode config = config();
        for (String id : DELIVERY_AGENTS) {
            JsonNode agent = registration(config, id);
            assertThat(agent).as("LiteLLM registration of " + id).isNotNull();
            JsonNode card = agent.get("agent_card_params");
            assertThat(card.get("protocolVersion").asString()).isEqualTo(PROTOCOL_VERSION);
            assertThat(card.get("url").asString()).isEqualTo("http://embabel-agent-runtime:8082/a2a/" + id);
            assertThat(card.get("capabilities").get("streaming").asBoolean()).isFalse();
        }
    }

    @Test
    void jsonPayloadMatchesTheYaml() throws IOException {
        JsonNode json = JsonMapper.builder().build().readTree(LITELLM.resolve("a2a/customer-support.agent.json").toFile());
        assertThat(json).isEqualTo(customerSupportRegistration());
    }

    @Test
    void referencedGuardrailExistsInTheLiteLlmConfig() throws IOException {
        JsonNode config = config();
        assertThat(config.get("guardrails").findValuesAsString("guardrail_name")).contains("openjcockpit-pii");
        assertThat(config.get("model_list").findValuesAsString("model_name")).contains("customer-chat");
    }

    @Test
    void theRuntimeImplementsTheRegisteredProtocol() throws IOException {
        String source = Files.readString(AgentRuntimeCheckout.module()
                .resolve("src/main/java/nl/metafactory/agentruntime/a2a/A2AProtocol.java"));
        assertThat(runtimeConstant(source, "PROTOCOL_VERSION")).isEqualTo(PROTOCOL_VERSION);
        assertThat(runtimeConstant(source, "TRANSPORT")).isEqualTo(TRANSPORT);
    }

    @Test
    void registrationsMatchTheRuntimesSeededDefinitions() throws IOException {
        Path agents = AgentRuntimeCheckout.module().resolve("agent-definitions/agents");
        JsonNode config = config();
        var models = config.get("model_list").findValuesAsString("model_name");
        var attachedModels = config.get("policy_attachments").get(0).get("models").valueStream()
                .map(JsonNode::asString).toList();
        var parser = new AgentMarkdownParser();
        for (String id : DELIVERY_AGENTS) {
            var doc = parser.parse(Files.readString(agents.resolve(id + ".md")));
            assertThat(doc.id()).isEqualTo(id);
            assertThat(doc.name()).isNotBlank();
            assertThat(doc.guardrails()).contains("pii-protection");
            assertThat(models).as("model alias of " + id).contains(doc.model());
            assertThat(attachedModels).as("PII policy on model of " + id).contains(doc.model());
            JsonNode card = registration(config, id).get("agent_card_params");
            assertThat(card.get("version").asString()).isEqualTo(doc.version());
            assertThat(card.get("description").asString()).isEqualTo(doc.description());
        }
        var customerSupport = parser.parse(Files.readString(agents.resolve("customer-support.md")));
        assertThat(customerSupportRegistration().get("agent_name").asString()).isEqualTo(customerSupport.id());
    }
}
