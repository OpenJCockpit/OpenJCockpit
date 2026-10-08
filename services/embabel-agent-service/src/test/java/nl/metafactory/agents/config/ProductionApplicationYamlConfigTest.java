package nl.metafactory.agents.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.web.ErrorProperties;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.PropertyPlaceholderHelper;

import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-QA-1 (workflow-approval-gate QA report §9), second follow-up review R-3: closes the one
 * remaining gap the lead identified in the D-QA-1 remediation itself. Both
 * {@link ErrorResponseContractIntegrationTest} and ai-control-service's
 * {@code EmbabelErrorRelayIntegrationTest} inject {@code server.error.include-message=always}
 * themselves via {@code @TestPropertySource}, because Maven's test classpath places
 * {@code src/test/resources} ahead of {@code src/main/resources}, so the test-scoped
 * {@code application.yml} silently shadows the production one for any test that boots the full
 * Spring context. That means the whole suite would stay green even if someone later deleted
 * {@code server.error.include-message: always} or
 * {@code spring.main.allow-bean-definition-overriding: true} from the REAL, deployed
 * {@code src/main/resources/application.yml} — a silent D-QA-1 regression with zero test failure.
 *
 * <p>This test reads that file directly off disk via a {@link FileSystemResource}, bypassing the
 * classpath (and therefore the shadowing) entirely, and binds it into
 * {@link org.springframework.boot.autoconfigure.web.ErrorProperties} at both the
 * {@code server.error} and {@code spring.web.error} prefixes — not a hand-rolled string comparison —
 * so it fails the same way a real misconfiguration would be diagnosed.
 */
class ProductionApplicationYamlConfigTest {

    // Surefire runs with the module root as working directory (see SpecRepositoryStructureTest).
    private final Path productionYaml = Path.of(System.getProperty("user.dir"), "src", "main", "resources", "application.yml");

    private Properties loadProductionYaml() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new FileSystemResource(productionYaml));
        Properties properties = factory.getObject();
        assertThat(properties)
                .as("real production application.yml must exist and parse at " + productionYaml)
                .isNotNull();
        return properties;
    }

    @Test
    void productionYamlKeepsIncludeMessageAlwaysSoErrorResponsesRetainTheirMessageField() {
        Properties properties = loadProductionYaml();

        // server.error.include-message is legacy/inert in Boot 4.1.1 (ServerProperties.getError()
        // was removed), but the key remains in the yaml for documentation purposes. We still assert
        // it binds to ALWAYS so the file stays self-documenting.
        ErrorProperties serverErrorProps = new Binder(new MapConfigurationPropertySource(properties))
                .bind("server.error", ErrorProperties.class)
                .orElseThrow(() -> new AssertionError("server.error.* did not bind from the real production application.yml"));

        assertThat(serverErrorProps.getIncludeMessage())
                .as("D-QA-1 regression guard (legacy): server.error.include-message must stay ALWAYS "
                        + "in the real embabel-agent-service application.yml for documentation purposes; "
                        + "this key no longer controls the HTTP response body in Boot 4.1.1")
                .isEqualTo(ErrorProperties.IncludeAttribute.ALWAYS);

        // spring.web.error.include-message is the operative key in Boot 4.1.1 that actually controls
        // the real HTTP error response body (WebProperties @ConfigurationProperties("spring.web")
        // has a nested ErrorProperties field). We assert it too so both paths are covered.
        ErrorProperties webErrorProps = new Binder(new MapConfigurationPropertySource(properties))
                .bind("spring.web.error", ErrorProperties.class)
                .orElseThrow(() -> new AssertionError("spring.web.error.* did not bind from the real production application.yml"));

        assertThat(webErrorProps.getIncludeMessage())
                .as("D-QA-1 regression guard (operative): spring.web.error.include-message must stay ALWAYS "
                        + "in Boot 4.1.1 — this is the key that actually controls the HTTP error response body")
                .isEqualTo(ErrorProperties.IncludeAttribute.ALWAYS);
    }

    @Test
    void productionYamlNoLongerSetsBeanDefinitionOverridingSinceErrorHandlingConfigWasDeleted() {
        Properties properties = loadProductionYaml();

        // ErrorHandlingConfig was deleted because Embabel 1.5.1 ships no competing ErrorController,
        // so the override flag that used to be required for it to register a same-named replacement
        // bean is no longer needed.
        assertThat(properties.getProperty("spring.main.allow-bean-definition-overriding"))
                .as("D-QA-1 regression guard: spring.main.allow-bean-definition-overriding must be "
                        + "absent from the real embabel-agent-service application.yml now that "
                        + "ErrorHandlingConfig has been removed — Embabel 1.5.1 no longer ships a "
                        + "competing ErrorController, so bean-definition overriding is unnecessary")
                .isNull();
    }

    @Test
    void ollamaAliasesAreParameterizedPlaceholdersNeverLiteralTags() {
        Properties properties = loadProductionYaml();

        assertThat(properties.getProperty("embabel.models.default-llm"))
                .as("ADR-003/F-2 regression guard: embabel.models.default-llm must stay the property "
                        + "placeholder ${openjcockpit.llm.ollama.model}, never a literal tag - otherwise "
                        + "overriding OLLAMA_MODEL at runtime would make ConfigurableModelProvider fail "
                        + "fast and the service would refuse to start")
                .isEqualTo("${openjcockpit.llm.ollama.model}");

        assertThat(properties.getProperty("embabel.models.llms.coding"))
                .as("ADR-003/F-2 regression guard: embabel.models.llms.coding must stay the property "
                        + "placeholder ${openjcockpit.llm.ollama.model}, never a literal tag - otherwise "
                        + "overriding OLLAMA_MODEL at runtime would make ConfigurableModelProvider fail "
                        + "fast and the service would refuse to start")
                .isEqualTo("${openjcockpit.llm.ollama.model}");
    }

    @Test
    void ollamaBaseUrlAndModelPropertiesHaveTheDocumentedPlaceholderShape() {
        Properties properties = loadProductionYaml();

        assertThat(properties.getProperty("openjcockpit.llm.ollama.model"))
                .as("openjcockpit.llm.ollama.model must stay overridable via OLLAMA_MODEL with the "
                        + "documented default qwen3.6:27b")
                .isEqualTo("${OLLAMA_MODEL:qwen3.6:27b}");

        assertThat(properties.getProperty("openjcockpit.llm.ollama.base-url"))
                .as("openjcockpit.llm.ollama.base-url must stay overridable via OLLAMA_BASE_URL with the "
                        + "documented loopback default")
                .isEqualTo("${OLLAMA_BASE_URL:http://localhost:11434}");
    }

    @Test
    void gatewayPropertiesHaveTheDocumentedPlaceholderShape() {
        Properties properties = loadProductionYaml();

        assertThat(properties.getProperty("openjcockpit.llm.gateway.base-url"))
                .as("openjcockpit.llm.gateway.base-url must stay overridable via LITELLM_BASE_URL with the "
                        + "documented default http://localhost:4000/v1")
                .isEqualTo("${LITELLM_BASE_URL:http://localhost:4000/v1}");

        assertThat(properties.getProperty("openjcockpit.llm.gateway.api-key"))
                .as("openjcockpit.llm.gateway.api-key must stay overridable via LITELLM_VIRTUAL_KEY with the "
                        + "documented local default sk-openjcockpit-embabel-local")
                .isEqualTo("${LITELLM_VIRTUAL_KEY:sk-openjcockpit-embabel-local}");

        assertThat(properties.getProperty("openjcockpit.llm.coding.route"))
                .as("BR-14 regression guard: openjcockpit.llm.coding.route is the configuration-only revert "
                        + "switch between the LiteLLM gateway route and the direct-Ollama route; its default "
                        + "must stay gateway so the LiteLLM gateway is the production-active route")
                .isEqualTo("${LLM_CODING_ROUTE:gateway}");

        PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}", ":", null, true);
        String resolvedReadTimeout = helper.replacePlaceholders(
                properties.getProperty("openjcockpit.llm.read-timeout-seconds"),
                properties::getProperty);

        assertThat(resolvedReadTimeout)
                .as("BR-19 pairing regression guard: openjcockpit.llm.read-timeout-seconds must resolve to 600 "
                        + "seconds, matching the embabel.agent.platform.llm-operations.prompts.default-timeout "
                        + "600s assertion in springAiModelSelectorsStayPinnedToOpenAiAndTheEmbabelOperationTimeoutResolvesTo600Seconds")
                .isEqualTo("600");
    }

    @Test
    void ollamaAliasesResolveToTheConfiguredModelTagUsingOnlyThePropertiesMapNeverTheLiveEnvironment() {
        Properties properties = loadProductionYaml();

        PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}", ":", null, true);

        String resolvedDefaultLlm = helper.replacePlaceholders(
                properties.getProperty("embabel.models.default-llm"), properties::getProperty);
        String resolvedCodingAlias = helper.replacePlaceholders(
                properties.getProperty("embabel.models.llms.coding"), properties::getProperty);

        assertThat(resolvedDefaultLlm)
                .as("embabel.models.default-llm must resolve to qwen3.6:27b using only the on-disk "
                        + "properties map, never System.getenv, so this test is deterministic even on a "
                        + "developer machine that happens to export OLLAMA_MODEL")
                .isEqualTo("qwen3.6:27b");

        assertThat(resolvedCodingAlias)
                .as("embabel.models.llms.coding must resolve to qwen3.6:27b using only the on-disk "
                        + "properties map, never System.getenv")
                .isEqualTo("qwen3.6:27b");
    }

    @Test
    void openAiAliasesAndEmbeddingConfigurationRemainUnchangedByTheOllamaAddition() {
        Properties properties = loadProductionYaml();

        assertThat(properties.getProperty("embabel.models.llms.best"))
                .as("embabel.models.llms.best must remain gpt-4.1-mini, unaffected by the Ollama addition")
                .isEqualTo("gpt-4.1-mini");

        assertThat(properties.getProperty("embabel.models.llms.cheapest"))
                .as("embabel.models.llms.cheapest must remain gpt-4.1-mini, unaffected by the Ollama addition")
                .isEqualTo("gpt-4.1-mini");

        assertThat(properties.getProperty("embabel.models.default-embedding-model"))
                .as("embabel.models.default-embedding-model must remain text-embedding-3-small - "
                        + "embeddings stay OpenAI-hosted per the requirement's Q5 answer")
                .isEqualTo("text-embedding-3-small");

        assertThat(properties.getProperty("spring.ai.openai.api-key"))
                .as("AC-20 regression guard: spring.ai.openai.api-key must stay the bare placeholder "
                        + "${OPENAI_API_KEY} with no default value, so startup still fails fast when the "
                        + "key is absent")
                .isEqualTo("${OPENAI_API_KEY}");
    }

    @Test
    void springAiModelSelectorsStayPinnedToOpenAiAndTheEmbabelOperationTimeoutResolvesTo600Seconds() {
        Properties properties = loadProductionYaml();

        assertThat(properties.getProperty("spring.ai.model.chat"))
                .as("ADR-005 regression guard: spring.ai.model.chat must stay pinned to openai so the "
                        + "Ollama starter's auto-configuration never activates")
                .isEqualTo("openai");

        assertThat(properties.getProperty("spring.ai.model.embedding"))
                .as("ADR-005 regression guard: spring.ai.model.embedding must stay pinned to openai so "
                        + "the Ollama starter's auto-configuration never activates")
                .isEqualTo("openai");

        PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}", ":", null, true);
        String resolvedTimeout = helper.replacePlaceholders(
                properties.getProperty("embabel.agent.platform.llm-operations.prompts.default-timeout"),
                properties::getProperty);

        assertThat(resolvedTimeout)
                .as("ADR-007/F-1 regression guard: embabel.agent.platform.llm-operations.prompts.default-timeout "
                        + "must resolve to 600s so it is not silently shorter than openjcockpit.llm.read-timeout-seconds")
                .isEqualTo("600s");
    }
}
