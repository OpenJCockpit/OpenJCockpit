package nl.metafactory.agentruntime.ops;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `docker compose up` runs the runtime as its own container: own image and port, no database, no shared
 * volume, the agent service and its own public A2A address by configurable URL, and LiteLLM routing A2A
 * agents to it.
 */
class ComposeRuntimeWiringContractTest {

    static final Path ROOT = Path.of("../..");

    static JsonNode yaml(String file) throws IOException {
        return YAMLMapper.builder().build().readTree(Files.newInputStream(ROOT.resolve(file)));
    }

    static JsonNode services() throws IOException {
        return yaml("docker-compose.yml").path("services");
    }

    @Test
    void theRuntimeIsASeparatelyBuiltAndPublishedContainer() throws IOException {
        JsonNode runtime = services().path("embabel-agent-runtime");
        assertThat(runtime.path("build").path("context").asString()).isEqualTo(".");
        assertThat(runtime.path("build").path("dockerfile").asString()).isEqualTo("services/embabel-agent-runtime/Dockerfile");
        assertThat(runtime.path("image").asString()).startsWith("openjcockpit/embabel-agent-runtime:");
        assertThat(runtime.path("container_name").asString()).isEqualTo("openjcockpit-embabel-runtime");
        assertThat(runtime.path("ports").get(0).asString()).isEqualTo("8092:8082");
        assertThat(runtime.path("healthcheck").path("test").toString()).contains("/actuator/health/readiness");
        assertThat(Files.exists(ROOT.resolve("services/embabel-agent-runtime/Dockerfile"))).isTrue();
    }

    @Test
    void theRuntimeHasNoDatabaseNoSharedVolumeAndNoStartDependencyOnTheAgentService() throws IOException {
        JsonNode runtime = services().path("embabel-agent-runtime");
        JsonNode env = runtime.path("environment");
        assertThat(env.has("DB_URL")).isFalse();
        assertThat(runtime.path("depends_on").has("postgres")).isFalse();
        assertThat(runtime.path("depends_on").has("embabel-agent-service")).isFalse();
        assertThat(runtime.path("volumes").valueStream().map(JsonNode::asString).toList())
                .containsExactly("./services/embabel-agent-runtime/agent-definitions:/agent-definitions:ro");
    }

    @Test
    void everyCrossServiceAddressIsConfigurable() throws IOException {
        JsonNode services = services();
        JsonNode env = services.path("embabel-agent-runtime").path("environment");
        assertThat(env.path("SPRING_PROFILES_ACTIVE").asString()).isEqualTo("local");
        assertThat(env.path("EMBABEL_AGENT_SERVICE_BASE_URL").asString())
                .isEqualTo("${EMBABEL_AGENT_SERVICE_BASE_URL:-http://embabel-agent-service:8081}");
        assertThat(env.path("A2A_PUBLIC_BASE_URL").asString())
                .isEqualTo("${A2A_PUBLIC_BASE_URL:-http://embabel-agent-runtime:8082}");
        assertThat(env.path("AGENT_RUNTIME_CLIENT_SECRET").asString())
                .isEqualTo(services.path("local-seed").path("environment").path("AGENT_RUNTIME_CLIENT_SECRET").asString());
        assertThat(env.path("LITELLM_VIRTUAL_KEY").asString())
                .isEqualTo(services.path("local-seed").path("environment").path("LITELLM_VIRTUAL_KEY").asString());
    }

    @Test
    void litellmForwardsToTheAddressTheLocalProfileAdvertises() throws IOException {
        JsonNode profile = YAMLMapper.builder().build().readTree(getClass().getResourceAsStream("/application-local.yml"));
        String advertised = profile.path("openjcockpit").path("agent-definitions").path("a2a")
                .path("public-base-url").asString().replaceAll("\\$\\{[A-Z0-9_]+:(.*)}", "$1");
        assertThat(advertised).isEqualTo("http://embabel-agent-runtime:8082");
        int agents = 0;
        for (JsonNode agent : yaml("infrastructure/litellm/config.yaml").path("agents")) {
            assertThat(agent.path("agent_card_params").path("url").asString())
                    .isEqualTo(advertised + "/a2a/" + agent.path("agent_name").asString());
            agents++;
        }
        assertThat(agents).isEqualTo(4);
        assertThat(profile.path("openjcockpit").path("agent-service").path("base-url").asString())
                .isEqualTo("${EMBABEL_AGENT_SERVICE_BASE_URL:http://embabel-agent-service:8081}");
    }

    @Test
    void theImageCarriesItsDefinitionsAndRunsUnprivileged() throws IOException {
        String dockerfile = Files.readString(ROOT.resolve("services/embabel-agent-runtime/Dockerfile"));
        assertThat(dockerfile).contains("-pl services/embabel-agent-runtime -am package",
                "COPY --from=build /workspace/services/embabel-agent-runtime/agent-definitions /agent-definitions",
                "USER 10001", "HEALTHCHECK", "EXPOSE 8082")
                .doesNotContain("embabel-agent-service");
    }
}
