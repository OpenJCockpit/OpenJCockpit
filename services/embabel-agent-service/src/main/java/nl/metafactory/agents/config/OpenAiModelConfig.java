package nl.metafactory.agents.config;

import com.embabel.agent.spi.support.springai.SpringAiLlmService;
import com.embabel.common.ai.model.DefaultOptionsConverter;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.ai.model.PricingModel;
import com.embabel.common.ai.model.SpringAiEmbeddingService;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Configures the OpenAI-backed {@link SpringAiLlmService} and embedding beans that Embabel's
 * platform discovers by type. Spring AI 2.0.1 removed the hand-buildable {@code OpenAiApi}/
 * {@code OpenAiChatModel} construction path this class previously used (built on
 * {@code com.openai.client.OpenAIClient} instead); this class now consumes the framework's own
 * auto-configured {@link OpenAiChatModel}/{@link OpenAiEmbeddingModel} beans (which also carries
 * Micrometer observation wiring for free) and applies connect/read timeouts via an
 * {@link OpenAiHttpClientBuilderCustomizer}, the exact functional replacement for the previous
 * hand-built {@code ReactorClientHttpRequestFactory} connect/read pair — both budgets (45s
 * connect, 600s read) are preserved separately, not merged.
 *
 * <p>Retry behavior: the previous {@code RetryUtils.DEFAULT_RETRY_TEMPLATE} wiring (Spring Retry)
 * no longer applies — {@code OpenAiChatModel.Builder} no longer exposes a retry-template seam in
 * Spring AI 2.0.1. Retries are now governed by the framework's own {@code spring.ai.openai.chat.max-retries}
 * property, whose effective default is {@code 3} (see application.yml comment). This is a disclosed,
 * accepted behavioral change for this delivery, not a silent regression.
 */
@Configuration
public class OpenAiModelConfig {

    @Bean
    OpenAiHttpClientBuilderCustomizer openAiHttpClientBuilderCustomizer(
            @Value("${openjcockpit.llm.connect-timeout-seconds:45}") long connectTimeoutSeconds,
            @Value("${openjcockpit.llm.read-timeout-seconds:600}") long readTimeoutSeconds) {
        return builder -> builder.timeout(com.openai.core.Timeout.builder()
                .connect(Duration.ofSeconds(connectTimeoutSeconds))
                .read(Duration.ofSeconds(readTimeoutSeconds))
                .request(Duration.ofSeconds(readTimeoutSeconds))
                .build());
    }

    @Bean
    SpringAiLlmService gpt41Mini(OpenAiChatModel openAiChatModel, ObservationRegistry observationRegistry) {
        return new SpringAiLlmService(
                "gpt-4.1-mini",
                "OpenAI",
                openAiChatModel,
                DefaultOptionsConverter.INSTANCE,
                LocalDate.of(2024, 6, 1),
                List.of(),
                PricingModel.usdPer1MTokens(0.40, 1.60));
    }

    @Bean
    EmbeddingService textEmbedding3Small(OpenAiEmbeddingModel openAiEmbeddingModel) {
        return new SpringAiEmbeddingService(
                "text-embedding-3-small",
                "OpenAI",
                openAiEmbeddingModel,
                null,
                null);
    }
}
