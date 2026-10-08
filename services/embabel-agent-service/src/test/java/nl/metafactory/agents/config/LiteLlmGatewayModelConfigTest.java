package nl.metafactory.agents.config;

import com.embabel.common.ai.model.LlmOptions;
import com.embabel.agent.spi.support.springai.SpringAiLlmService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LiteLlmGatewayModelConfigTest {

    private static final String VALID_CHAT_COMPLETION_RESPONSE_BODY = """
            {"id":"chatcmpl-test","object":"chat.completion","created":1,"model":"my-test-tag",
             "choices":[{"index":0,"message":{"role":"assistant","content":"pong"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
            """;

    private final LiteLlmGatewayModelConfig config = new LiteLlmGatewayModelConfig();
    private final List<MockWebServer> servers = new ArrayList<>();

    private MockWebServer newServer() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();
        servers.add(server);
        return server;
    }

    @AfterEach
    void tearDown() throws IOException {
        for (MockWebServer server : servers) {
            server.shutdown();
        }
        servers.clear();
    }

    // Pins: a successful chat completion request is OpenAI-shaped, hits /v1/chat/completions, carries the model tag and Bearer auth, and returns the assistant text.
    @Test
    void happyPathSendsOpenAiShapedRequestToV1ChatCompletions() throws Exception {
        MockWebServer server = newServer();
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(VALID_CHAT_COMPLETION_RESPONSE_BODY));

        SpringAiLlmService llm = config.litellmCodingLlm(server.url("/v1").toString(), "my-test-key", "my-test-tag", 45L, 600L, ObservationRegistry.create());

        ChatResponse response = llm.getModel().call(new Prompt("ping"));

        assertThat(server.getRequestCount()).isEqualTo(1);
        RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getPath()).isEqualTo("/v1/chat/completions");

        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode requestBody = mapper.readTree(recorded.getBody().readUtf8());
        assertThat(requestBody.get("model").asString()).isEqualTo("my-test-tag");

        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer my-test-key");

        assertThat(response.getResult().getOutput().getText()).isNotBlank();
    }

    // Pins: constructing the bean is inert and performs no network I/O.
    @Test
    void constructionIsInertAndMakesNoNetworkCall() throws Exception {
        MockWebServer server = newServer();
        SpringAiLlmService llm = assertDoesNotThrow(() -> config.litellmCodingLlm(server.url("/v1").toString(), "some-key", "qwen3.6:27b", 45L, 600L, ObservationRegistry.create()));

        assertThat(llm).isNotNull();
        assertThat(server.getRequestCount()).isEqualTo(0);
    }

    // Pins: bean metadata (name, provider, zero pricing, /no_think prompt contributor) reflects the injected arguments.
    @Test
    void beanMetadataReflectsInjectedArguments() {
        SpringAiLlmService llm = config.litellmCodingLlm("http://127.0.0.1:1/v1", "some-key", "llama3.1:8b", 45L, 600L, ObservationRegistry.create());

        assertThat(llm.getName()).isEqualTo("llama3.1:8b");
        assertThat(llm.getProvider()).isEqualTo("LiteLLM");
        assertThat(llm.getPricingModel()).isNotNull();
        assertThat(llm.getPricingModel().usdPerInputToken()).isEqualTo(0.0);
        assertThat(llm.getPricingModel().usdPerOutputToken()).isEqualTo(0.0);
        assertThat(llm.getPromptContributors()).hasSize(1);
        assertThat(llm.getPromptContributors().get(0).contribution()).isEqualTo("/no_think");
    }

    // Pins: an unreachable gateway fails without leaking the prompt text, the API key, or Authorization headers into the error message.
    @Test
    void unreachableGatewayFailsWithoutLeakingPromptKeyOrHeaders() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        String baseUrl = "http://127.0.0.1:" + port + "/v1";
        SpringAiLlmService llm = config.litellmCodingLlm(baseUrl, "super-secret-gateway-key-xyz", "qwen3.6:27b", 45L, 2L, ObservationRegistry.create());

        Exception thrown = assertThrows(Exception.class, () -> llm.getModel().call(new Prompt("super secret prompt text")));

        StringBuilder fullMessage = new StringBuilder();
        Throwable current = thrown;
        while (current != null) {
            if (current.getMessage() != null) {
                fullMessage.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }

        assertThat(fullMessage.toString()).contains("127.0.0.1:" + port);
        assertThat(fullMessage.toString()).doesNotContain("super secret prompt text");
        assertThat(fullMessage.toString()).doesNotContain("super-secret-gateway-key-xyz");
        assertThat(fullMessage.toString()).doesNotContain("Authorization");
    }

    // Pins: an unreachable gateway fails fast within a bounded retry window.
    @Test
    void unreachableGatewayFailsFastWithBoundedRetry() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        String baseUrl = "http://127.0.0.1:" + port + "/v1";
        SpringAiLlmService llm = config.litellmCodingLlm(baseUrl, "some-key", "qwen3.6:27b", 45L, 2L, ObservationRegistry.create());

        Instant before = Instant.now();
        assertThrows(Exception.class, () -> llm.getModel().call(new Prompt("ping")));
        Instant after = Instant.now();

        Duration elapsed = Duration.between(before, after);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(15));
    }

    // Pins: a 401 unauthorized gateway response fails closed with exactly one request (no retry loop).
    @Test
    void unauthorizedGatewayResponseFailsClosedWithoutRetryLoop() throws Exception {
        MockWebServer server = newServer();
        server.enqueue(new MockResponse().setResponseCode(401));

        SpringAiLlmService llm = config.litellmCodingLlm(server.url("/v1").toString(), "some-key", "qwen3.6:27b", 45L, 600L, ObservationRegistry.create());

        assertThrows(Exception.class, () -> llm.getModel().call(new Prompt("ping")));

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    // Its 1s/30s cases against a 3s server delay never approached the real ~60s ceiling that was
    // live-verified in production; slowResponsePastOldSixtySecondCeilingSucceedsWithinConfiguredBudget
    // (below) closes that gap by testing past the actual ceiling.
    // Pins: the read timeout is honoured behaviorally — a short timeout fails while a long timeout succeeds against the same slow response.
    @Test
    void timeoutIsHonouredBehaviorally() throws Exception {
        MockWebServer slowServer = newServer();
        slowServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(VALID_CHAT_COMPLETION_RESPONSE_BODY).setBodyDelay(3, TimeUnit.SECONDS));
        SpringAiLlmService shortTimeoutLlm = config.litellmCodingLlm(slowServer.url("/v1").toString(), "some-key", "my-test-tag", 45L, 1L, ObservationRegistry.create());
        assertThrows(Exception.class, () -> shortTimeoutLlm.getModel().call(new Prompt("ping")));

        MockWebServer patientServer = newServer();
        patientServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(VALID_CHAT_COMPLETION_RESPONSE_BODY).setBodyDelay(3, TimeUnit.SECONDS));
        SpringAiLlmService longTimeoutLlm = config.litellmCodingLlm(patientServer.url("/v1").toString(), "some-key", "my-test-tag", 45L, 30L, ObservationRegistry.create());
        ChatResponse response = assertDoesNotThrow(() -> longTimeoutLlm.getModel().call(new Prompt("ping")));
        assertThat(response.getResult().getOutput().getText()).isNotBlank();
    }

    // Pins: the gateway options converter maps supported params (temperature, topP, maxTokens, frequencyPenalty, model) and deliberately omits presencePenalty and topK.
    @Test
    void gatewayOptionsConverterMapsSupportedParamsAndDeliberatelyOmitsPresencePenaltyAndTopK() {
        LlmOptions fullOptions = LlmOptions.withDefaults().withTemperature(0.5).withTopP(0.9).withTopK(40).withMaxTokens(256).withPresencePenalty(0.1).withFrequencyPenalty(0.2);
        ChatOptions converted = LiteLlmGatewayModelConfig.convertToGatewayOptions(600L, fullOptions, "some-model-tag");

        assertThat(converted.getTemperature()).isEqualTo(0.5);
        assertThat(converted.getTopP()).isEqualTo(0.9);
        assertThat(converted.getMaxTokens()).isEqualTo(256);
        assertThat(converted.getFrequencyPenalty()).isEqualTo(0.2);
        assertThat(converted.getModel()).isEqualTo("some-model-tag");
        assertThat(converted.getPresencePenalty()).isNull();
        assertThat(converted.getTopK()).isNull();
        assertThat(((org.springframework.ai.openai.OpenAiChatOptions) converted).getTimeout()).isEqualTo(java.time.Duration.ofSeconds(600));

        LlmOptions nullOptions = LlmOptions.withDefaults();
        ChatOptions convertedNulls = assertDoesNotThrow(() -> LiteLlmGatewayModelConfig.convertToGatewayOptions(600L, nullOptions, "some-model-tag"));
        assertThat(convertedNulls).isNotNull();
    }

    // Pins: the fix for the ~60s client-side ceiling defect -- a response delayed past the old
    // buggy ceiling (but within the configured 90s budget) succeeds when driven through the real
    // options-converter call path used by every real Embabel interaction (not a hand-built
    // OpenAiChatOptions with timeout set directly, which would not have caught the original bug).
    @Test
    void slowResponsePastOldSixtySecondCeilingSucceedsWithinConfiguredBudget() throws Exception {
        MockWebServer server = newServer();
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(VALID_CHAT_COMPLETION_RESPONSE_BODY).setBodyDelay(65, TimeUnit.SECONDS));

        SpringAiLlmService llm = config.litellmCodingLlm(server.url("/v1").toString(), "some-key", "my-test-tag", 45L, 90L, ObservationRegistry.create());

        ChatResponse response = assertDoesNotThrow(() -> llm.getModel().call(new Prompt("ping")));
        assertThat(response.getResult().getOutput().getText()).isNotBlank();
    }

    // Pins: the actual gatewayOptionsConverter lambda captured in litellmCodingLlm (not just the
    // static convertToGatewayOptions method it delegates to) is wired correctly end-to-end via
    // SpringAiLlmService.convertOptions -- this is the exact invocation path used by every real
    // Embabel interaction.
    @Test
    void springAiLlmServiceConvertOptionsInvokesGatewayConverterWithConfiguredTimeout() {
        SpringAiLlmService llm = config.litellmCodingLlm("http://127.0.0.1:1/v1", "some-key", "qwen3.6:27b", 45L, 600L, ObservationRegistry.create());

        LlmOptions llmOptions = LlmOptions.withDefaults().withTemperature(0.3);
        ChatOptions converted = llm.convertOptions(llmOptions);

        assertThat(converted).isInstanceOf(org.springframework.ai.openai.OpenAiChatOptions.class);
        assertThat(((org.springframework.ai.openai.OpenAiChatOptions) converted).getTimeout()).isEqualTo(java.time.Duration.ofSeconds(600));
        assertThat(converted.getTemperature()).isEqualTo(0.3);
    }
}
