package nl.metafactory.agents.config;

import com.embabel.common.ai.model.ConfigurableModelProvider;
import com.embabel.common.ai.model.ConfigurableModelProviderProperties;
import com.embabel.common.ai.model.DefaultOptionsConverter;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.agent.spi.support.springai.SpringAiLlmService;
import com.embabel.common.ai.model.SpringAiEmbeddingService;
import com.embabel.common.ai.model.ModelSelectionCriteria;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.PropertyPlaceholderHelper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * T-2 (architecture §12.2): {@link ConfigurableModelProvider} is a plain Kotlin class, not a
 * Spring bean, and every {@code @SpringBootTest} in this module stubs {@code ModelProvider} via
 * {@code @MockitoBean}, so alias resolution against the real production {@code application.yml}
 * is otherwise never exercised. This test constructs the provider directly from the on-disk
 * production YAML (bypassing the test-classpath-shadowed {@code src/test/resources/application.yml})
 * and proves the {@code coding} alias and platform default both resolve to the Ollama model, while
 * {@code best}/{@code cheapest} still resolve to {@code gpt-4.1-mini}. It also doubles as the
 * regression guard for finding F-2: if a future edit hardcodes a literal tag in the alias while the
 * bean name comes from an overridden {@code OLLAMA_MODEL}, this test starts failing loudly instead
 * of the real service refusing to boot.
 */
class OllamaModelResolutionTest {

    private final Path productionYaml =
            Path.of(System.getProperty("user.dir"), "src", "main", "resources", "application.yml");

    private Properties loadProductionYamlResolved() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new FileSystemResource(productionYaml));
        Properties raw = factory.getObject();
        assertThat(raw)
                .as("real production application.yml must exist and parse at " + productionYaml)
                .isNotNull();

        PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}", ":", null, true);
        Properties resolved = new Properties();
        for (String name : raw.stringPropertyNames()) {
            resolved.setProperty(name, helper.replacePlaceholders(raw.getProperty(name), raw::getProperty));
        }
        return resolved;
    }

    @Test
    void codingAliasAndPlatformDefaultResolveToOllamaWhileBestAndCheapestStayOnOpenAi() {
        Properties resolved = loadProductionYamlResolved();

        ConfigurableModelProviderProperties properties = new Binder(new MapConfigurationPropertySource(resolved))
                .bind("embabel.models", ConfigurableModelProviderProperties.class)
                .orElseThrow(() -> new AssertionError("embabel.models did not bind from the real production application.yml"));

        String ollamaTag = resolved.getProperty("openjcockpit.llm.ollama.model");
        assertThat(ollamaTag).as("resolved Ollama model tag").isNotBlank();

        // The production-active bean is now the LiteLLM gateway one, so this test must exercise the path
        // production actually takes. ADR-003/F-2 alias resolution is unaffected because both routes derive
        // the LLM's registered name from the same openjcockpit.llm.ollama.model property.
        SpringAiLlmService codingLlm = new LiteLlmGatewayModelConfig().litellmCodingLlm(
                resolved.getProperty("openjcockpit.llm.gateway.base-url"),
                resolved.getProperty("openjcockpit.llm.gateway.api-key"),
                ollamaTag,
                45L,
                600L,
                ObservationRegistry.create());

        SpringAiLlmService gpt41MiniStub = new SpringAiLlmService(
                "gpt-4.1-mini",
                "OpenAI",
                mock(ChatModel.class),
                DefaultOptionsConverter.INSTANCE,
                null,
                List.of(),
                null);

        EmbeddingService textEmbedding3SmallStub = new SpringAiEmbeddingService(
                "text-embedding-3-small",
                "OpenAI",
                mock(EmbeddingModel.class),
                null,
                null);

        ConfigurableModelProvider modelProvider = new ConfigurableModelProvider(
                List.of(codingLlm, gpt41MiniStub),
                List.of(textEmbedding3SmallStub),
                properties);

        assertThat(modelProvider.getLlm(ModelSelectionCriteria.byRole("coding")).getName())
                .as("embabel.models.llms.coding must resolve to the Ollama model")
                .isEqualTo(ollamaTag);

        assertThat(modelProvider.getLlm(ModelSelectionCriteria.getPlatformDefault()).getName())
                .as("embabel.models.default-llm must resolve to the Ollama model")
                .isEqualTo(ollamaTag);

        assertThat(modelProvider.getLlm(ModelSelectionCriteria.byRole("best")).getName())
                .as("embabel.models.llms.best must remain gpt-4.1-mini")
                .isEqualTo("gpt-4.1-mini");

        assertThat(modelProvider.getLlm(ModelSelectionCriteria.byRole("cheapest")).getName())
                .as("embabel.models.llms.cheapest must remain gpt-4.1-mini")
                .isEqualTo("gpt-4.1-mini");
    }

    @Test
    void codingAliasAndPlatformDefaultResolveToOllamaViaDirectRouteToo() {
        Properties resolved = loadProductionYamlResolved();

        ConfigurableModelProviderProperties properties = new Binder(new MapConfigurationPropertySource(resolved))
                .bind("embabel.models", ConfigurableModelProviderProperties.class)
                .orElseThrow(() -> new AssertionError("embabel.models did not bind from the real production application.yml"));

        String ollamaTag = resolved.getProperty("openjcockpit.llm.ollama.model");
        assertThat(ollamaTag).as("resolved Ollama model tag").isNotBlank();

        SpringAiLlmService directOllamaLlm = new OllamaModelConfig().ollamaCodingLlm(
                resolved.getProperty("openjcockpit.llm.ollama.base-url"),
                ollamaTag,
                45L,
                600L,
                ObservationRegistry.create());

        SpringAiLlmService gpt41MiniStub = new SpringAiLlmService(
                "gpt-4.1-mini",
                "OpenAI",
                mock(ChatModel.class),
                DefaultOptionsConverter.INSTANCE,
                null,
                List.of(),
                null);

        EmbeddingService textEmbedding3SmallStub = new SpringAiEmbeddingService(
                "text-embedding-3-small",
                "OpenAI",
                mock(EmbeddingModel.class),
                null,
                null);

        ConfigurableModelProvider modelProvider = new ConfigurableModelProvider(
                List.of(directOllamaLlm, gpt41MiniStub),
                List.of(textEmbedding3SmallStub),
                properties);

        assertThat(modelProvider.getLlm(ModelSelectionCriteria.byRole("coding")).getName())
                .as("embabel.models.llms.coding must resolve to the Ollama model on the direct route too, proving "
                        + "alias resolution is name-based and route-independent")
                .isEqualTo(ollamaTag);

        assertThat(modelProvider.getLlm(ModelSelectionCriteria.getPlatformDefault()).getName())
                .as("embabel.models.default-llm must resolve to the Ollama model on the direct route too")
                .isEqualTo(ollamaTag);

        assertThat(modelProvider.getLlm(ModelSelectionCriteria.byRole("best")).getName())
                .as("embabel.models.llms.best must remain gpt-4.1-mini")
                .isEqualTo("gpt-4.1-mini");

        assertThat(modelProvider.getLlm(ModelSelectionCriteria.byRole("cheapest")).getName())
                .as("embabel.models.llms.cheapest must remain gpt-4.1-mini")
                .isEqualTo("gpt-4.1-mini");
    }
}
