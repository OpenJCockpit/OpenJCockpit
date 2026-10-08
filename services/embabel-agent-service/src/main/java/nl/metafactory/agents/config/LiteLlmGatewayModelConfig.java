package nl.metafactory.agents.config;

import com.embabel.agent.spi.support.springai.SpringAiLlmService;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.model.OptionsConverter;
import com.embabel.common.ai.model.PricingModel;
import com.embabel.common.ai.prompt.PromptContributor;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Production-active LLM route for the coding agent.
 *
 * <p>LiteLLM is reached over {@code POST {baseUrl}/v1/chat/completions} because the
 * gateway does not serve Ollama's native {@code /api/chat} endpoint. The 30-minute
 * model residency ({@code "keep_alive"}) parity with the direct-Ollama path now lives
 * server-side in the gateway's own config file rather than in this class.
 *
 * <p>The Micrometer {@code gen_ai.system} observation provider tag changes from the
 * Ollama convention to the OpenAI convention as a result of this route. This is a
 * disclosed, deliberate behavioral change, not an oversight.
 *
 * <p>A separate, already-investigated defect is intentionally NOT addressed here: the outer
 * Embabel data-binding retry wrapper ({@code com.embabel.agent.spi.support.LlmDataBindingProperties},
 * a final class in embabel-agent-api with a single Spring-managed instance shared by
 * {@code ChatClientLlmOperations} across every LLM route -- this gateway route, the direct-Ollama
 * route, and the OpenAI route) retries indiscriminately on any exception except two hardcoded,
 * semantically-unrelated types it excludes ({@code ReplanRequestedException} and
 * {@code GuardRailViolationException}). There is no extensibility hook to make it skip retrying on
 * an HTTP 401/403 from this gateway specifically, and its {@code max-attempts} property is a single
 * value shared across all three routes, so lowering it would also affect the OpenAI/gpt41Mini path
 * and legitimate JSON-binding-retry scenarios on this same gateway route. Reusing
 * {@code GuardRailViolationException} or {@code ReplanRequestedException} to signal an authentication
 * failure was deliberately rejected as a workaround: both are semantically wrong for this purpose and
 * would produce misleading logs/behavior elsewhere in the platform. This is a documented, out-of-scope
 * finding, not something this class attempts to fix.
 */
@Configuration
@ConditionalOnProperty(name = "metafactory.llm.coding.route", havingValue = "gateway", matchIfMissing = true)
public class LiteLlmGatewayModelConfig {

    private static final Logger log = LoggerFactory.getLogger(LiteLlmGatewayModelConfig.class);

    /**
     * Builds the {@link SpringAiLlmService} that routes coding-agent LLM calls through
     * the LiteLLM gateway using the OpenAI chat-completions wire protocol.
     *
     * <p>Both {@code .timeout(...)} and {@code .maxRetries(0)} are set explicitly on the
     * {@link OpenAiChatOptions} builder used to construct this bean because the defaults
     * (60-second timeout, 3 retries) are fatal for a slow local LLM behind this gateway. This
     * build-time {@code .timeout(...)} configures the base OpenAI SDK client correctly but is
     * NOT, by itself, sufficient for every real request: Spring AI's {@code OpenAiChatModel}
     * uses whatever {@link OpenAiChatOptions} is attached to the actual {@code Prompt} for a
     * given call, and does not merge it with this bean's build-time defaults when the prompt's
     * options are already non-null (see {@code OpenAiChatModel.buildRequestPrompt(Prompt)}).
     * Every real Embabel-orchestrated call supplies its own {@link OpenAiChatOptions}, produced
     * by the options converter defined further down in this method, so that per-call converter
     * is what actually carries {@code .timeout(...)} for real traffic; the build-time value here
     * exists only to configure the initial client construction correctly.
     *
     * <p>The {@link OpenAiChatModel} and {@link OpenAiChatOptions} instances are local
     * variables only and are never exposed as Spring beans.
     *
     * @param baseUrl             the LiteLLM gateway base URL (OpenAI-compatible endpoint)
     * @param apiKey              the API key for the gateway
     * @param model               the model identifier to request
     * @param connectTimeoutSeconds the HTTP connect timeout in seconds
     * @param readTimeoutSeconds  the HTTP read timeout in seconds
     * @param observationRegistry the Micrometer observation registry for tracing
     * @return a fully configured {@link SpringAiLlmService} for the coding agent
     */
    @Bean
    SpringAiLlmService litellmCodingLlm(
            @Value("${metafactory.llm.gateway.base-url:http://localhost:4000/v1}") String baseUrl,
            @Value("${metafactory.llm.gateway.api-key:sk-metafactory-embabel-local}") String apiKey,
            @Value("${metafactory.llm.ollama.model:qwen3.6:27b}") String model,
            @Value("${metafactory.llm.connect-timeout-seconds:45}") long connectTimeoutSeconds,
            @Value("${metafactory.llm.read-timeout-seconds:600}") long readTimeoutSeconds,
            ObservationRegistry observationRegistry) {
        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .model(model)
                .timeout(Duration.ofSeconds(readTimeoutSeconds))
                .maxRetries(0)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .options(chatOptions)
                .observationRegistry(observationRegistry)
                .httpClientBuilderCustomizer(builder -> builder.timeout(
                        com.openai.core.Timeout.builder()
                                .connect(Duration.ofSeconds(connectTimeoutSeconds))
                                .read(Duration.ofSeconds(readTimeoutSeconds))
                                .request(Duration.ofSeconds(readTimeoutSeconds))
                                .build()))
                .toolCallingManager(ToolCallingManager.builder()
                        .observationRegistry(observationRegistry)
                        .build())
                .build();

        log.info("Coding LLM configured via LiteLLM gateway: model={} baseUrl={}", model, baseUrl);

        OptionsConverter gatewayOptionsConverter =
                (llmOptions, modelName) -> convertToGatewayOptions(readTimeoutSeconds, llmOptions, modelName);

        return new SpringAiLlmService(
                model,
                "LiteLLM",
                chatModel,
                gatewayOptionsConverter,
                LocalDate.of(2025, 4, 1),
                List.of(NO_THINK_PROMPT_CONTRIBUTOR),
                PricingModel.usdPer1MTokens(0.0, 0.0));
    }

    static final PromptContributor NO_THINK_PROMPT_CONTRIBUTOR = PromptContributor.fixed("/no_think");

    /**
     * Converts {@link LlmOptions} to gateway-specific {@link OpenAiChatOptions}.
     *
     * <p>{@code presence_penalty} is not among {@code ollama_chat}'s supported OpenAI
     * params on the LiteLLM gateway side, and the gateway config uses
     * {@code drop_params: false}, so LiteLLM raises {@code UnsupportedParamsError}
     * (HTTP 500) if presence_penalty is sent. Sending it would be a live production
     * defect.
     *
     * <p>{@code topK} cannot be expressed on the OpenAI wire protocol at all;
     * {@code OpenAiChatOptions.getTopK()} returns null unconditionally regardless of
     * what the builder's inherited {@code topK(...)} setter is called with.
     *
     * <p>{@code maxTokens} maps to OpenAI's {@code max_tokens}, which LiteLLM maps
     * onward to Ollama's {@code num_predict} — exact behavioral parity with the
     * existing direct-Ollama path's {@code .numPredict(...)} mapping.
     *
     * <p>{@code com.embabel.common.ai.model.DefaultOptionsConverter.INSTANCE} must never
     * be used in this class instead of this method — it propagates presencePenalty,
     * which would cause the HTTP 500 described above.
     *
     * @param readTimeoutSeconds the HTTP read timeout in seconds
     * @param options the source LLM options
     * @param model   the model identifier
     * @return gateway-compatible {@link OpenAiChatOptions}
     */
    static org.springframework.ai.chat.prompt.ChatOptions convertToGatewayOptions(long readTimeoutSeconds, LlmOptions options, String model) {
        return OpenAiChatOptions.builder()
                .model(model)
                .temperature(options.getTemperature())
                .topP(options.getTopP())
                .maxTokens(options.getMaxTokens())
                .frequencyPenalty(options.getFrequencyPenalty())
                .timeout(Duration.ofSeconds(readTimeoutSeconds))
                .build();
    }
}
