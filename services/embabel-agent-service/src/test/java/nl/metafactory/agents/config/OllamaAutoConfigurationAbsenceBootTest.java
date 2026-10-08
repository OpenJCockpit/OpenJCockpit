package nl.metafactory.agents.config;

import com.embabel.agent.spi.support.springai.SpringAiLlmService;
import com.embabel.common.ai.model.ModelProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-4 (architecture §12.2), AC-9 / BR-8: proves that adding the {@code spring-ai-starter-model-ollama}
 * dependency does not silently register competing auto-configured chat/embedding beans alongside the
 * hand-built {@link OllamaModelConfig#ollamaCodingLlm} and {@link OpenAiModelConfig#gpt41Mini}. Both
 * {@code OllamaChatAutoConfiguration} and {@code OllamaEmbeddingAutoConfiguration} default to
 * {@code matchIfMissing = true}; ADR-005 pins {@code spring.ai.model.chat}/{@code embedding} to
 * {@code openai} in both {@code application.yml} files to suppress them. This test boots the full
 * production auto-configuration graph (subject to the test-classpath-shadowed
 * {@code src/test/resources/application.yml}, which carries the same ADR-005 pinning) and asserts
 * their absence directly on the {@link ApplicationContext}, rather than trusting the property alone.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class OllamaAutoConfigurationAbsenceBootTest {

    @MockitoBean ModelProvider modelProvider;

    @Autowired ApplicationContext context;

    @Test
    void noOllamaAutoConfiguredBeansExistInTheContext() {
        assertThat(context.getBeanNamesForType(OllamaChatModel.class))
                .as("no auto-configured OllamaChatModel bean must exist - ADR-005 pins spring.ai.model.chat to openai")
                .isEmpty();
        assertThat(context.getBeanNamesForType(OllamaEmbeddingModel.class))
                .as("no auto-configured OllamaEmbeddingModel bean must exist - ADR-005 pins spring.ai.model.embedding to openai")
                .isEmpty();
        // Spring AI 2.0.1 disclosed deviation: OllamaApiAutoConfiguration registers a standalone
        // "ollamaApi" bean gated ONLY on @ConditionalOnClass(OllamaApi.class) — i.e. purely on
        // classpath presence of the Ollama starter, NOT on spring.ai.model.chat/embedding like its
        // siblings OllamaChatAutoConfiguration/OllamaEmbeddingAutoConfiguration. This bean is inert
        // (no network call), unused by this application's own wiring, and does not interfere with
        // OllamaModelConfig's hand-built OllamaApi instance or which ChatModel/EmbeddingModel beans
        // are active. We accept exactly one such bean as a documented Spring AI 2.0.1 behavior change.
        assertThat(context.getBeanNamesForType(OllamaApi.class))
                .as("Spring AI 2.0.1 disclosed deviation: OllamaApiAutoConfiguration is always-on (classpath-gated only); expect exactly the harmless 'ollamaApi' bean and nothing else")
                .containsExactly("ollamaApi");
    }

    @Test
    void chatModelAndEmbeddingModelBeanCountsMatchThePreOllamaBaseline() {
        // This assertion also now guards against ever silently exposing the LiteLLM gateway's OpenAiChatModel as a Spring bean,
        // which would trip @ConditionalOnMissingBean on the auto-configured openAiChatModel bean and silently repoint gpt41Mini at the gateway.
        assertThat(context.getBeanNamesForType(ChatModel.class))
                .as("WP-0 baseline: exactly one ChatModel bean (openAiChatModel) must remain - adding the "
                        + "Ollama starter must not register a second, competing ChatModel bean")
                .containsExactly("openAiChatModel");
        assertThat(context.getBeanNamesForType(EmbeddingModel.class))
                .as("WP-0 baseline: exactly one EmbeddingModel bean (openAiEmbeddingModel) must remain")
                .containsExactly("openAiEmbeddingModel");
    }

    @Test
    void bothHandBuiltLlmBeansArePresentAndNoOthers() {
        // The default/coding LLM now transits the LiteLLM gateway by default (BR-13/ADR-1);
        // the Ollama-native bean is retained but is now conditional on `openjcockpit.llm.coding.route=direct` (BR-14 Revert B).
        assertThat(Set.of(context.getBeanNamesForType(SpringAiLlmService.class)))
                .as("exactly the two hand-built Llm beans must be registered, by name")
                .isEqualTo(Set.of("litellmCodingLlm", "gpt41Mini"));
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
            properties = "openjcockpit.llm.coding.route=direct")
    class DirectRouteRevertB {

        @Test
        void directRouteActivatesTheOllamaNativeBeanSetInsteadOfTheGatewayOne() {
            assertThat(Set.of(context.getBeanNamesForType(SpringAiLlmService.class)))
                    .as("setting openjcockpit.llm.coding.route=direct must activate the pre-gateway bean set")
                    .isEqualTo(Set.of("ollamaCodingLlm", "gpt41Mini"));
            assertThat(Set.of(context.getBeanNamesForType(ChatModel.class)))
                    .as("the direct route must not expose any extra ChatModel bean")
                    .isEqualTo(Set.of("openAiChatModel"));
        }
    }
}
