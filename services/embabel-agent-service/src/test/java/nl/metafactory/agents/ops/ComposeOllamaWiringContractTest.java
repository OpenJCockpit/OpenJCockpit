package nl.metafactory.agents.ops;

import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComposeOllamaWiringContractTest {

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

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    @Test
    void ollamaBaseUrlDefaultsToHostGatewayAddress() throws IOException {
        // KNOWN LIBRARY LIMITATION: this module's tools.jackson:jackson-dataformat-yaml version
        // resolves a YAML *alias reference (e.g. services.embabel-agent-service.environment
        // .OLLAMA_BASE_URL, which is `OLLAMA_BASE_URL: *ollama-base-url` in docker-compose.yml)
        // to the alias/anchor NAME string ("ollama-base-url"), not to the anchor's defined
        // scalar value, when read via YAMLMapper.readTree(...).path(...).asString(). This was
        // independently reproduced by two engineers on this exact classpath with a minimal
        // repro file; `docker compose config` (Docker's own engine) resolves the same file
        // correctly, so this is a parser-library limitation, not a docker-compose.yml defect.
        // Reading the anchor's own DEFINITION node (the top-level `x-ollama-base-url` document
        // key `x-ollama-base-url: &ollama-base-url ...`) is unaffected, because that is a
        // definition, not a reference, and parses to the real value. This test therefore reads
        // the definition node rather than the alias reference, while still proving the exact
        // same literal-value property the test originally asserted. It also guards BR-14's
        // Revert-B target: embabel-agent-service must still be able to talk to the host Ollama
        // daemon directly if the LiteLLM gateway route is ever reverted. The gateway URL itself
        // is asserted separately, by litellmBaseUrlIsTheGatewayServiceAndNotLoopback.
        JsonNode root = parseCompose();
        String value = root.path("x-ollama-base-url").asString();
        assertEquals("${OLLAMA_BASE_URL:-http://host.docker.internal:11434}", value);
    }

    @Test
    void ollamaBaseUrlDoesNotReferenceLocalhostOrLoopback() throws IOException {
        // Read the anchor's DEFINITION node (top-level `x-ollama-base-url`) rather than the
        // services...environment...OLLAMA_BASE_URL alias reference, because of the same
        // Jackson-YAML alias-resolution limitation documented in
        // ollamaBaseUrlDefaultsToHostGatewayAddress above (see that method for the full
        // rationale). Reading the alias path would resolve to the anchor NAME string, making
        // the assertions below vacuously true.
        JsonNode root = parseCompose();
        String value = root.path("x-ollama-base-url").asString();
        assertFalse(value.contains("localhost"));
        assertFalse(value.contains("127.0.0.1"));
    }

    @Test
    void extraHostsMapsDockerInternalToHostGateway() throws IOException {
        JsonNode root = parseCompose();
        JsonNode extraHosts = root.path("services")
                .path("embabel-agent-service")
                .path("extra_hosts");
        assertTrue(extraHosts.isArray());
        assertEquals(1, extraHosts.size());
        assertEquals("host.docker.internal:host-gateway", extraHosts.get(0).asString());

        // litellm is now also granted host access (BR-17): it is the component that reaches
        // the host Ollama daemon, so it needs the same host.docker.internal mapping.
        JsonNode litellmExtraHosts = root.path("services")
                .path("litellm")
                .path("extra_hosts");
        assertTrue(litellmExtraHosts.isArray());
        assertEquals(1, litellmExtraHosts.size());
        assertEquals("host.docker.internal:host-gateway", litellmExtraHosts.get(0).asString());
    }

    @Test
    void extraHostsIsGrantedToExactlyTheTwoAllowedServices() throws IOException {
        // BR-17 (signed off via Q2): host.docker.internal access is granted to exactly
        // {embabel-agent-service, litellm}. litellm needs it because it is now the component
        // that calls the host Ollama daemon. embabel-agent-service retains it because that
        // grant is exactly what makes BR-14's Revert B a configuration-only operation instead
        // of a docker-compose.yml edit during an incident.
        //
        // This is an exact two-element allow-list asserted by set equality in both directions:
        // a third service gaining extra_hosts still fails this test. It is not a relaxation to
        // "any service may have extra_hosts" -- renamed from noOtherServiceHasExtraHosts, whose
        // original name and single-service assertion no longer matched the intended contract
        // once litellm legitimately needed the same grant.
        JsonNode root = parseCompose();
        JsonNode servicesNode = root.path("services");
        Set<String> allowed = Set.of("embabel-agent-service", "litellm");
        Set<String> actual = new HashSet<>();
        for (String name : servicesNode.propertyNames()) {
            if (!servicesNode.path(name).path("extra_hosts").isMissingNode()) {
                actual.add(name);
            }
        }
        assertEquals(allowed, actual,
                "host.docker.internal access must be granted to exactly {embabel-agent-service, litellm}");
    }

    @Test
    void noOllamaServiceOrImageIsPresent() throws IOException {
        JsonNode root = parseCompose();
        JsonNode servicesNode = root.path("services");

        // 5a: no explicit "ollama" service
        assertTrue(servicesNode.path("ollama").isMissingNode());

        // 5b: no service image contains "ollama"
        for (String name : servicesNode.propertyNames()) {
            JsonNode imageNode = servicesNode.path(name).path("image");
            if (!imageNode.isMissingNode()) {
                assertFalse(imageNode.asString().toLowerCase().contains("ollama"),
                        "Service '" + name + "' image must not contain 'ollama'");
            }
        }
    }

    @Test
    void openAiApiKeyEnvironmentEntryIsUnchanged() throws IOException {
        JsonNode root = parseCompose();
        String value = root.path("services")
                .path("embabel-agent-service")
                .path("environment")
                .path("OPENAI_API_KEY")
                .asString();
        assertEquals("${OPENAI_API_KEY}", value);

        // BR-13: the OpenAI key must never be forwarded to the gateway container -- the
        // gateway only ever talks to the host Ollama daemon, never to OpenAI.
        assertTrue(root.path("services")
                        .path("litellm")
                        .path("environment")
                        .path("OPENAI_API_KEY")
                        .isMissingNode(),
                "BR-13: OPENAI_API_KEY must never be forwarded to the gateway container");
    }

    @Test
    void noHealthcheckReferencesOllamaPort() throws IOException {
        JsonNode root = parseCompose();
        JsonNode servicesNode = root.path("services");

        // Extended from a single-service check to all services: no service's healthcheck may
        // reference the host Ollama port directly, since the gateway now owns that access.
        for (String name : servicesNode.propertyNames()) {
            JsonNode healthcheckNode = servicesNode.path(name).path("healthcheck");
            if (!healthcheckNode.isMissingNode()) {
                assertFalse(healthcheckNode.toString().contains("11434"),
                        "Service '" + name + "' healthcheck must not reference port 11434");
            }
        }

        String litellmHealthcheck = servicesNode.path("litellm").path("healthcheck").toString();
        assertTrue(litellmHealthcheck.contains("/health/liveliness"),
                "the litellm healthcheck must probe /health/liveliness");
        assertFalse(Pattern.compile("/health(?![/A-Za-z])").matcher(litellmHealthcheck).find(),
                "the litellm healthcheck must never use the bare /health route: it is authenticated and "
                        + "health-checks every model in config.yaml, i.e. it would call the host Ollama daemon "
                        + "and convert 'healthy but non-functional' into 'the stack refuses to start' (BR-5/BR-16)");
    }

    @Test
    void volumesKeySetIsUnchanged() throws IOException {
        JsonNode root = parseCompose();
        JsonNode volumesNode = root.path("volumes");
        Set<String> volumeNames = new HashSet<>();
        for (String name : volumesNode.propertyNames()) {
            volumeNames.add(name);
        }
        assertEquals(Set.of("workflow-workspaces", "workflow-definitions", "git-mcp-workspaces"), volumeNames);
    }

    @Test
    void litellmBaseUrlIsTheGatewayServiceAndNotLoopback() throws IOException {
        JsonNode root = parseCompose();
        String value = root.path("services")
                .path("embabel-agent-service")
                .path("environment")
                .path("LITELLM_BASE_URL")
                .asString();
        assertEquals("http://litellm:4000/v1", value);
        assertFalse(value.contains("localhost"));
        assertFalse(value.contains("127.0.0.1"));
    }

    @Test
    void embabelDependsOnLitellmHealthy() throws IOException {
        // AC-12 / BR-16 / D-2: embabel-agent-service must not start serving requests before the
        // gateway is healthy and its virtual key exists, and the gateway itself must not start
        // before its own database dependencies are ready.
        JsonNode root = parseCompose();
        JsonNode servicesNode = root.path("services");

        assertEquals("service_healthy", servicesNode.path("embabel-agent-service")
                .path("depends_on").path("litellm").path("condition").asString());
        assertEquals("service_completed_successfully", servicesNode.path("embabel-agent-service")
                .path("depends_on").path("litellm-seed-key").path("condition").asString());
        assertEquals("service_healthy", servicesNode.path("litellm")
                .path("depends_on").path("postgres").path("condition").asString());
        assertEquals("service_completed_successfully", servicesNode.path("litellm")
                .path("depends_on").path("postgres-init").path("condition").asString());
    }

    @Test
    void modelTagHasExactlyOneSourceOfTruth() throws IOException {
        // BR-18 / AC-27 / R-11: OLLAMA_MODEL and OLLAMA_BASE_URL must each have exactly one
        // definition (a YAML anchor), consumed by both embabel-agent-service and litellm via
        // *alias references, not two coincidentally-equal literals.
        //
        // Because of the same Jackson YAML alias-resolution limitation documented in
        // ollamaBaseUrlDefaultsToHostGatewayAddress above, assertions 1 and 3 below prove the
        // single-source-of-truth property indirectly but just as strongly: both services parse
        // to the identical alias name, which is only possible if they reference the same single
        // YAML anchor. Assertions 2 and 4 independently confirm there is exactly one textual
        // anchor definition for each variable (not two coincidentally-equal literals). Together
        // these four assertions prove BR-18 without depending on the alias resolving to its
        // scalar value.
        JsonNode root = parseCompose();
        JsonNode servicesNode = root.path("services");

        String embabelModel = servicesNode.path("embabel-agent-service")
                .path("environment").path("OLLAMA_MODEL").asString();
        String litellmModel = servicesNode.path("litellm")
                .path("environment").path("OLLAMA_MODEL").asString();
        assertEquals(embabelModel, litellmModel,
                "OLLAMA_MODEL must resolve to the same single YAML anchor for both services");
        assertEquals("ollama-model", embabelModel,
                "OLLAMA_MODEL alias name must be exactly 'ollama-model'");

        String composeText = Files.readString(repoRoot().resolve("docker-compose.yml"));
        assertEquals(1, countOccurrences(composeText, "${OLLAMA_MODEL:-"),
                "OLLAMA_MODEL must have exactly one anchor definition, proving a shared anchor "
                        + "rather than two coincidentally-equal literals");

        String embabelBaseUrl = servicesNode.path("embabel-agent-service")
                .path("environment").path("OLLAMA_BASE_URL").asString();
        String litellmBaseUrl = servicesNode.path("litellm")
                .path("environment").path("OLLAMA_BASE_URL").asString();
        assertEquals(embabelBaseUrl, litellmBaseUrl,
                "OLLAMA_BASE_URL must resolve to the same single YAML anchor for both services");
        assertEquals("ollama-base-url", embabelBaseUrl,
                "OLLAMA_BASE_URL alias name must be exactly 'ollama-base-url'");

        assertEquals(1, countOccurrences(composeText, "${OLLAMA_BASE_URL:-"),
                "OLLAMA_BASE_URL must have exactly one anchor definition, proving a shared anchor "
                        + "rather than two coincidentally-equal literals");
    }

    @Test
    void litellmConfigIsAReadOnlyBindMountAndNotANamedVolume() throws IOException {
        // BR-20: the LiteLLM config file is operator-editable local configuration, not runtime
        // state, so it must be a read-only bind mount rather than a named Docker volume.
        JsonNode root = parseCompose();
        JsonNode volumes = root.path("services").path("litellm").path("volumes");
        assertTrue(volumes.isArray());
        assertEquals(1, volumes.size());
        String mount = volumes.get(0).asString();
        assertTrue(mount.startsWith("./infrastructure/litellm/"),
                "the litellm config mount must be a repository-relative bind mount");
        assertTrue(mount.endsWith(":ro"),
                "the litellm config mount must be read-only");
    }
}
