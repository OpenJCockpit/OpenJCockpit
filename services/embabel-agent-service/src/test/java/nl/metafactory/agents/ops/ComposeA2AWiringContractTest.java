package nl.metafactory.agents.ops;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `docker compose up` alone gives a working local A2A setup: one seed service, one Spring profile,
 * one A2A key for the LiteLLM provider, consistent with the LiteLLM agent registration.
 */
class ComposeA2AWiringContractTest {

    static final Path ROOT = Path.of("../..");

    static JsonNode yaml(String file) throws IOException {
        return YAMLMapper.builder().build().readTree(Files.newInputStream(ROOT.resolve(file)));
    }

    static JsonNode services() throws IOException {
        return yaml("docker-compose.yml").path("services");
    }

    @Test
    void embabelRunsTheLocalProfileWithOnlyPathsAndSecretsFromCompose() throws IOException {
        JsonNode service = services().path("embabel-agent-service");
        JsonNode env = service.path("environment");
        assertThat(env.path("SPRING_PROFILES_ACTIVE").asString()).isEqualTo("local");
        assertThat(env.path("LITELLM_A2A_KEY").asString()).isNotBlank();
        assertThat(env.has("A2A_ENABLED")).isFalse();
        assertThat(env.has("AGENT_DEFINITIONS_ENABLED")).isFalse();
        assertThat(service.path("depends_on").path("local-seed").path("condition").asString())
                .isEqualTo("service_completed_successfully");
        assertThat(service.path("volumes").valueStream().map(JsonNode::asString).toList())
                .contains("./agents:/agents:ro",
                        "./services/embabel-agent-service/agent-definitions:/agent-definitions:ro");
    }

    @Test
    void oneSeedServiceRunsWithoutComposeProfiles() throws IOException {
        JsonNode services = services();
        assertThat(services.has("litellm-seed-key")).isFalse();
        assertThat(services.has("keycloak-a2a-client")).isFalse();
        JsonNode seed = services.path("local-seed");
        assertThat(seed.has("profiles")).as("must run on a plain `docker compose up`").isFalse();
        JsonNode env = seed.path("environment");
        assertThat(env.path("LITELLM_A2A_KEY").asString())
                .isEqualTo(services.path("embabel-agent-service").path("environment").path("LITELLM_A2A_KEY").asString());
        assertThat(env.path("A2A_CLIENT_ID").asString()).isEqualTo("openjcockpit-a2a");
        assertThat(env.path("A2A_TENANT").asString()).isEqualTo("local");
        assertThat(seed.path("entrypoint").get(1).asString()).isEqualTo("/local-seed/seed-local.sh");
    }

    @Test
    void theOneLiteLlmA2AKeyCoversListingCardsAndCalls() throws IOException {
        String keys = Files.readString(ROOT.resolve("infrastructure/local-seed/litellm-keys.sh"));
        assertThat(keys).contains("\"allowed_routes\":[\"/v1/agents\",\"agent_inference_routes\"]",
                "LITELLM_A2A_KEY_ALIAS:-openjcockpit-a2a}", "/key/delete");
        assertThat(Files.readString(ROOT.resolve("infrastructure/local-seed/keycloak-service-clients.sh")))
                .contains("\"serviceAccountsEnabled\":true", "\"directAccessGrantsEnabled\":false",
                        "oidc-hardcoded-claim-mapper");
    }

    @Test
    void litellmForwardsToTheAddressTheLocalProfileAdvertises() throws IOException {
        JsonNode profile = YAMLMapper.builder().build().readTree(
                getClass().getResourceAsStream("/application-local.yml"));
        String advertised = profile.path("openjcockpit").path("agent-definitions").path("a2a")
                .path("public-base-url").asString().replaceAll("\\$\\{[A-Z0-9_]+:(.*)}", "$1");
        int agents = 0;
        for (JsonNode agent : yaml("infrastructure/litellm/config.yaml").path("agents")) {
            assertThat(agent.path("agent_card_params").path("url").asString())
                    .isEqualTo(advertised + "/a2a/" + agent.path("agent_name").asString());
            agents++;
        }
        assertThat(agents).isEqualTo(4);
    }

    @Test
    void localSeedTenantMatchesTheKeycloakClaim() throws IOException {
        String tenant = services().path("local-seed").path("environment").path("A2A_TENANT").asString();
        String seed = new String(getClass().getResourceAsStream("/db/seed-local/R__seed_local_agent_bindings.sql")
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(seed).contains("'" + tenant + "', 'business-analyst'", "'" + tenant + "', 'qa-engineer'");
    }

    /** The local git MCP server is a separate MCP server registered with LiteLLM and granted to the agent-service key. */
    @Test
    void gitMcpServerIsRegisteredWithLiteLlmAndGrantedToTheAgentServiceKey() throws IOException {
        JsonNode services = services();
        JsonNode server = yaml("infrastructure/litellm/config.yaml").path("mcp_servers").path("openjcockpit_git");
        assertThat(server.path("server_id").asString()).isEqualTo("openjcockpit_git");
        assertThat(server.path("transport").asString())
                .as("git-mcp-server pins spring.ai.mcp.server.protocol: sse").isEqualTo("sse");
        assertThat(server.path("url").asString()).isEqualTo("http://git-mcp-server:8093/sse");
        assertThat(server.path("allow_all_keys").asBoolean(true)).isFalse();
        assertThat(server.path("available_on_public_internet").asBoolean(true)).isFalse();
        assertThat(services.path("git-mcp-server").has("ports"))
                .as("no host port: the only way to the git MCP server is LiteLLM").isFalse();
        assertThat(services.path("litellm").path("depends_on").has("git-mcp-server")).isTrue();
        assertThat(new String(Files.readAllBytes(ROOT.resolve("services/git-mcp-server/src/main/resources/application.yml"))))
                .contains("protocol: sse");

        String keys = Files.readString(ROOT.resolve("infrastructure/local-seed/litellm-keys.sh"));
        assertThat(keys).contains("LITELLM_MCP_SERVERS:-openjcockpit_git}",
                "\"object_permission\":{\"mcp_servers\":[%s]}");
        assertThat(keys.indexOf("${mcp_grant}")).as("grant applied on create and on update")
                .isNotEqualTo(keys.lastIndexOf("${mcp_grant}"));
    }

    /**
     * Git-workspace jobs: ai-control and git-mcp-server have their own Keycloak service clients and the
     * git MCP server gets job credentials from ai-control, never through LiteLLM or a tool argument.
     */
    @Test
    void gitWorkspaceJobsUseOwnServiceIdentitiesAndAnMcpOnlyKey() throws IOException {
        JsonNode services = services();
        String clients = Files.readString(ROOT.resolve("infrastructure/local-seed/keycloak-service-clients.sh"));
        assertThat(clients).contains("GIT_MCP_CLIENT_ID:-openjcockpit-git-mcp", "AI_CONTROL_CLIENT_ID:-openjcockpit-ai-control");

        JsonNode gitMcp = services.path("git-mcp-server").path("environment");
        assertThat(gitMcp.path("GIT_MCP_AI_CONTROL_URL").asString()).isEqualTo("http://ai-control-service:8080");
        assertThat(gitMcp.path("GIT_MCP_CLIENT_ID").asString()).isEqualTo("openjcockpit-git-mcp");
        assertThat(gitMcp.path("GIT_MCP_CLIENT_SECRET").asString())
                .isEqualTo(services.path("local-seed").path("environment").path("GIT_MCP_CLIENT_SECRET").asString());

        JsonNode aiControl = services.path("ai-control-service");
        assertThat(aiControl.path("environment").path("AI_CONTROL_SERVICE_CLIENT_ID").asString()).isEqualTo("openjcockpit-ai-control");
        assertThat(aiControl.path("environment").path("LITELLM_AI_CONTROL_KEY").asString())
                .isEqualTo(services.path("local-seed").path("environment").path("LITELLM_AI_CONTROL_KEY").asString());
        assertThat(aiControl.path("depends_on").path("local-seed").path("condition").asString())
                .isEqualTo("service_completed_successfully");

        String keys = Files.readString(ROOT.resolve("infrastructure/local-seed/litellm-keys.sh"));
        assertThat(keys).contains("LITELLM_AI_CONTROL_KEY_ALIAS:-ai-control-service",
                "\"allowed_routes\":[\"/mcp-rest/tools/call\",\"/mcp-rest/tools/list\"]");
        assertThat(Files.readString(ROOT.resolve("infrastructure/local-seed/seed-local.sh")))
                .contains("keycloak-service-clients.sh", "litellm-keys.sh");
    }
}
