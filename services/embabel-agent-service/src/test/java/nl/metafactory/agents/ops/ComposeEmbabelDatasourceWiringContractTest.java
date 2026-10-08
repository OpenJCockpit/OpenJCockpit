package nl.metafactory.agents.ops;

import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComposeEmbabelDatasourceWiringContractTest {

    private static Path repoRoot() {
        Path candidate = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 10 && candidate != null; i++) {
            if (Files.exists(candidate.resolve("docker-compose.yml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate repository root (docker-compose.yml marker) "
                + "walking up from " + Paths.get("").toAbsolutePath());
    }

    private JsonNode parseCompose() throws IOException {
        return YAMLMapper.builder().build().readTree(Files.newInputStream(repoRoot().resolve("docker-compose.yml")));
    }

    @Test
    void dbUrlPointsAtEmbabelDatabase() throws IOException {
        JsonNode root = parseCompose();
        JsonNode environment = root.path("services")
                .path("embabel-agent-service")
                .path("environment");
        String value = environment.path("DB_URL").asString();
        assertEquals("jdbc:postgresql://postgres:5432/embabel", value);
        assertFalse(value.contains("openjcockpit"));
    }

    @Test
    void dbUsernameIsEmbabelAppNotOpenjcockpitApp() throws IOException {
        JsonNode root = parseCompose();
        JsonNode environment = root.path("services")
                .path("embabel-agent-service")
                .path("environment");
        String value = environment.path("DB_USERNAME").asString();
        assertEquals("embabel_app", value);
        assertFalse("openjcockpit_app".equals(value));
    }

    @Test
    void dbPasswordIsPresent() throws IOException {
        JsonNode root = parseCompose();
        JsonNode environment = root.path("services")
                .path("embabel-agent-service")
                .path("environment");
        assertFalse(environment.path("DB_PASSWORD").isMissingNode());
        assertFalse(environment.path("DB_PASSWORD").asString().isBlank());
    }

    @Test
    void dependsOnHasPostgresHealthyCondition() throws IOException {
        JsonNode root = parseCompose();
        assertEquals("service_healthy",
                root.path("services")
                        .path("embabel-agent-service")
                        .path("depends_on")
                        .path("postgres")
                        .path("condition")
                        .asString());
    }

    @Test
    void dependsOnHasPostgresInitCompletedCondition() throws IOException {
        JsonNode root = parseCompose();
        assertEquals("service_completed_successfully",
                root.path("services")
                        .path("embabel-agent-service")
                        .path("depends_on")
                        .path("postgres-init")
                        .path("condition")
                        .asString());
    }

    @Test
    void dependsOnPreservesStartedConditionForOpaAndGitMcpServer() throws IOException {
        JsonNode root = parseCompose();
        assertEquals("service_started",
                root.path("services")
                        .path("embabel-agent-service")
                        .path("depends_on")
                        .path("opa")
                        .path("condition")
                        .asString());
        assertEquals("service_started",
                root.path("services")
                        .path("embabel-agent-service")
                        .path("depends_on")
                        .path("git-mcp-server")
                        .path("condition")
                        .asString());
    }
}
