package nl.metafactory.agents.config;

import com.embabel.agent.api.models.OllamaModels;
import com.embabel.agent.spi.support.springai.SpringAiLlmService;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.model.OptionsConverter;
import com.embabel.common.ai.model.PricingModel;
import com.embabel.common.ai.prompt.PromptContributor;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Registers the locally hosted Ollama Qwen model as an additional {@link com.embabel.agent.spi.support.springai.SpringAiLlmService}, alongside the
 * OpenAI model in {@link OpenAiModelConfig}. Hand-built against {@code org.springframework.ai.ollama}
 * (bypassing Spring AI's Ollama auto-configuration) because
 * {@code spring-ai-autoconfigure-model-ollama} exposes no per-request connect/read timeout
 * configuration property: the auto-configured RestClient has no explicit request factory,
 * which resolves to {@link ReactorClientHttpRequestFactory}'s hardcoded 10-second response timeout
 * - unworkable for a local 27B model. Bean construction is inert: {@link PullModelStrategy#NEVER}
 * is set explicitly so no network call or model pull happens at context-refresh time; the first
 * byte on the wire is the first actual chat call.
 * <p>
 * This class is now the deliberately-retained BR-14 "Revert B" path. It is activated by setting the property
 * {@code metafactory.llm.coding.route} to {@code direct} while {@code OLLAMA_BASE_URL} still points at the host
 * Ollama daemon. The container's {@code extra_hosts} grant for {@code host.docker.internal} access is retained
 * specifically to keep this revert path viable without any {@code docker-compose.yml} edit.
 */
@ConditionalOnProperty(name = "metafactory.llm.coding.route", havingValue = "direct")
@Configuration
public class OllamaModelConfig {

    private static final Logger log = LoggerFactory.getLogger(OllamaModelConfig.class);

    @Bean
    SpringAiLlmService ollamaCodingLlm(
            @Value("${metafactory.llm.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${metafactory.llm.ollama.model:qwen3.6:27b}") String model,
            @Value("${metafactory.llm.connect-timeout-seconds:45}") long connectTimeoutSeconds,
            @Value("${metafactory.llm.read-timeout-seconds:600}") long readTimeoutSeconds,
            ObservationRegistry observationRegistry) {
        var requestFactory = new ReactorClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        // The non-streaming /api/chat path goes through the RestClient above (its read timeout is
        // what actually guards a slow local model). The WebClient below is Spring AI's streaming
        // path; left unconfigured it carries no read timeout of its own, so it is given the same
        // connect/read budget here for consistency and so a future streaming call fails the same
        // way instead of hanging indefinitely or timing out on an unrelated default.
        var reactorHttpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) Duration.ofSeconds(connectTimeoutSeconds).toMillis())
                .responseTimeout(Duration.ofSeconds(readTimeoutSeconds));

        var ollamaApi = OllamaApi.builder()
                .baseUrl(baseUrl)
                .restClientBuilder(RestClient.builder()
                        .requestFactory(requestFactory)
                        .observationRegistry(observationRegistry))
                .webClientBuilder(WebClient.builder()
                        .clientConnector(new ReactorClientHttpConnector(reactorHttpClient))
                        .observationRegistry(observationRegistry))
                .build();

        var chatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(OllamaChatOptions.builder()
                        .model(model)
                        // Keep the model resident on the Ollama daemon for 30m after the last
                        // request, so back-to-back agent-pipeline calls don't each pay the
                        // multi-GB reload cost for a 27B model.
                        .keepAlive("30m")
                        .build())
                .retryTemplate(boundedOllamaRetryTemplate())
                .toolCallingManager(ToolCallingManager.builder().observationRegistry(observationRegistry).build())
                .modelManagementOptions(ModelManagementOptions.builder()
                        .pullModelStrategy(PullModelStrategy.NEVER)
                        .build())
                .observationRegistry(observationRegistry)
                .build();

        log.info("Ollama coding LLM configured: model={} baseUrl={}", model, baseUrl);

        return new SpringAiLlmService(
                model,
                OllamaModels.PROVIDER,
                chatModel,
                OLLAMA_OPTIONS_CONVERTER,
                LocalDate.of(2025, 4, 1), // informational placeholder, not verified against a model card
                List.of(NO_THINK_PROMPT_CONTRIBUTOR),
                PricingModel.usdPer1MTokens(0.0, 0.0));
    }

    /**
     * Bounded retry template for Ollama calls.
     * maxRetries(0) means no retry — fail on the first attempt. This matches the original
     * Spring AI 1.0.0 behavior where Ollama had NO retry at all.
     * Worst-case wall-clock: 1 attempt x 600s read-timeout = ~600 seconds (~10 minutes).
     */
    private static RetryTemplate boundedOllamaRetryTemplate() {
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(0)
                .includes(TransientAiException.class)
                .includes(ResourceAccessException.class)
                .delay(Duration.ofMillis(500))
                .build();
        return new RetryTemplate(retryPolicy);
    }

    /**
     * {@code org.springframework.ai.ollama.api.OllamaChatOptions} in Spring AI 2.0.1 DOES have a
     * native thinkOption()/thinkLow()/thinkMedium()/thinkHigh() API (verified present). The
     * "/no_think" prompt-based workaround below is RETAINED DELIBERATELY as an explicit, deferred
     * scope decision for this delivery (not because it is still technically necessary) — adopting
     * the native think option is separate, deferred future work, not done as part of this port.
     * Embabel's {@link com.embabel.agent.spi.support.springai.SpringAiLlmService} joins its prompt
     * contributors into a single SystemMessage prepended to every call (see
     * com.embabel.agent.spi.support.springai.ChatClientLlmOperations#doTransform), so this is
     * injected there rather than requiring every calling agent to append it to its own prompt.
     * Caveat: Qwen's own documentation frames this control as most reliable in the user turn, not
     * the system message -- effectiveness here should be verified against a real response (does the
     * model still emit a reasoning trace?), not assumed from the directive's mere presence.
     */
    static final PromptContributor NO_THINK_PROMPT_CONTRIBUTOR = PromptContributor.fixed("/no_think");

    static final OptionsConverter OLLAMA_OPTIONS_CONVERTER = OllamaModelConfig::convertToOllamaOptions;

    static org.springframework.ai.chat.prompt.ChatOptions convertToOllamaOptions(LlmOptions options, String model) {
        return OllamaChatOptions.builder()
                .model(model)
                .temperature(options.getTemperature())
                .topP(options.getTopP())
                .topK(options.getTopK())
                .numPredict(options.getMaxTokens())
                .presencePenalty(options.getPresencePenalty())
                .frequencyPenalty(options.getFrequencyPenalty())
                .build();
    }
}
