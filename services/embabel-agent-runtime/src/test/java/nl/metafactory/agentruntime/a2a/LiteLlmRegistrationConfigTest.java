package nl.metafactory.agentruntime.a2a;

import nl.metafactory.common.agentdefinition.AgentMarkdownParser;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps the LiteLLM registration in lock-step with what this server actually implements. */
class LiteLlmRegistrationConfigTest {

    static final Path DIR = Path.of("../../infrastructure/litellm/a2a");

    @Test
    void registrationPinsTheProtocolVersionTheServerImplements() throws Exception {
        JsonNode agent = new YAMLMapper().readTree(DIR.resolve("customer-support.agent.yaml").toFile()).get("agents").get(0);
        JsonNode card = agent.get("agent_card_params");
        var definition = new AgentMarkdownParser().parse(
                Files.readString(Path.of("src/test/resources/agent-definitions/agents/customer-support.md")));

        assertThat(agent.get("agent_name").asString()).isEqualTo(definition.id());
        assertThat(card.get("protocolVersion").asString()).isEqualTo(A2AProtocol.PROTOCOL_VERSION);
        assertThat(card.get("preferredTransport").asString()).isEqualTo(A2AProtocol.TRANSPORT);
        assertThat(card.get("url").asString()).endsWith("/a2a/" + definition.id());
        assertThat(card.get("capabilities").get("streaming").asBoolean()).isFalse();
        assertThat(card.get("capabilities").get("pushNotifications").asBoolean()).isFalse();
        assertThat(agent.get("litellm_params").get("guardrails").get(0).asString()).isEqualTo("openjcockpit-pii");
    }

    @Test
    void seededDeliveryAgentsAreServedAndRegisteredConsistently() throws Exception {
        JsonNode config = new YAMLMapper().readTree(Path.of("../../infrastructure/litellm/config.yaml").toFile());
        var models = config.get("model_list").findValuesAsString("model_name");
        var attachedModels = config.get("policy_attachments").get(0).get("models").valueStream()
                .map(JsonNode::asString).toList();
        var parser = new AgentMarkdownParser();
        for (String id : java.util.List.of("business-analyst", "solution-architect", "software-developer", "qa-engineer")) {
            var doc = parser.parse(Files.readString(Path.of("agent-definitions/agents/" + id + ".md")));
            assertThat(doc.id()).isEqualTo(id);
            assertThat(doc.name()).isNotBlank();
            assertThat(doc.guardrails()).contains("pii-protection");
            assertThat(models).as("model alias of " + id).contains(doc.model());
            assertThat(attachedModels).as("PII policy on model of " + id).contains(doc.model());

            JsonNode agent = null;
            for (JsonNode a : config.get("agents")) {
                if (id.equals(a.get("agent_name").asString())) {
                    agent = a;
                }
            }
            assertThat(agent).as("LiteLLM registration of " + id).isNotNull();
            JsonNode card = agent.get("agent_card_params");
            assertThat(card.get("protocolVersion").asString()).isEqualTo(A2AProtocol.PROTOCOL_VERSION);
            assertThat(card.get("url").asString()).isEqualTo("http://embabel-agent-runtime:8082/a2a/" + id);
            assertThat(card.get("version").asString()).isEqualTo(doc.version());
            assertThat(card.get("description").asString()).isEqualTo(doc.description());
            assertThat(card.get("capabilities").get("streaming").asBoolean()).isFalse();
        }
    }

    @Test
    void jsonPayloadMatchesTheYaml() throws Exception {
        JsonNode yaml = new YAMLMapper().readTree(DIR.resolve("customer-support.agent.yaml").toFile()).get("agents").get(0);
        JsonNode json = JsonMapper.builder().build().readTree(DIR.resolve("customer-support.agent.json").toFile());
        assertThat(json).isEqualTo(yaml);
    }

    @Test
    void referencedGuardrailExistsInTheLiteLlmConfig() throws Exception {
        JsonNode config = new YAMLMapper().readTree(Path.of("../../infrastructure/litellm/config.yaml").toFile());
        assertThat(config.get("guardrails").findValuesAsString("guardrail_name")).contains("openjcockpit-pii");
        assertThat(config.get("model_list").findValuesAsString("model_name")).contains("customer-chat");
    }
}
