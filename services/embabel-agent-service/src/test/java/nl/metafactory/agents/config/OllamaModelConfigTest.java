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

class OllamaModelConfigTest {

    private static final String VALID_CHAT_RESPONSE_BODY = """
            {"model":"my-test-tag","created_at":"2026-01-01T00:00:00Z",
             "message":{"role":"assistant","content":"pong"},
             "done":true,"done_reason":"stop",
             "total_duration":1,"load_duration":1,
             "prompt_eval_count":1,"prompt_eval_duration":1,
             "eval_count":1,"eval_duration":1}
            """;

    private final OllamaModelConfig config = new OllamaModelConfig();
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

    @Test
    void happyPathSendsConfiguredModelTagToWireAndReturnsResponse() throws Exception {
        MockWebServer server = newServer();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(VALID_CHAT_RESPONSE_BODY));

        SpringAiLlmService llm = config.ollamaCodingLlm(
                server.url("/").toString(),
                "my-test-tag",
                45L,
                600L,
                ObservationRegistry.create());

        ChatResponse response = llm.getModel().call(new Prompt("ping"));

        assertThat(server.getRequestCount()).isEqualTo(1);
        RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getPath()).isEqualTo("/api/chat");

        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode requestBody = mapper.readTree(recorded.getBody().readUtf8());
        assertThat(requestBody.get("model").asString()).isEqualTo("my-test-tag");
        assertThat(requestBody.get("keep_alive").asString()).isEqualTo("30m");

        assertThat(response.getResult().getOutput().getText()).isNotBlank();
    }

    @Test
    void beanMetadataReflectsInjectedNonDefaultArguments() {
        SpringAiLlmService llm = config.ollamaCodingLlm(
                "http://127.0.0.1:1",
                "llama3.1:8b",
                45L,
                600L,
                ObservationRegistry.create());

        assertThat(llm.getName()).isEqualTo("llama3.1:8b");
        assertThat(llm.getProvider()).isEqualTo("Ollama");
        assertThat(llm.getPricingModel()).isNotNull();
        assertThat(llm.getPricingModel().usdPerInputToken()).isEqualTo(0.0);
        assertThat(llm.getPricingModel().usdPerOutputToken()).isEqualTo(0.0);
        assertThat(llm.getPromptContributors()).hasSize(1);
        assertThat(llm.getPromptContributors().get(0).contribution()).isEqualTo("/no_think");
    }

    @Test
    void constructionIsInertAndMakesNoNetworkCall() throws Exception {
        MockWebServer server = newServer();

        SpringAiLlmService llm = assertDoesNotThrow(() -> config.ollamaCodingLlm(
                server.url("/").toString(),
                "qwen3.6:27b",
                45L,
                600L,
                ObservationRegistry.create()));

        assertThat(llm).isNotNull();
        assertThat(server.getRequestCount()).isEqualTo(0);
    }

    @Test
    void unreachableEndpointFailsWithoutLeakingPromptOrHeaders() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        String baseUrl = "http://127.0.0.1:" + port;

        SpringAiLlmService llm = config.ollamaCodingLlm(baseUrl, "qwen3.6:27b", 45L, 2L, ObservationRegistry.create());

        Exception thrown = assertThrows(Exception.class,
                () -> llm.getModel().call(new Prompt("super secret prompt text")));

        StringBuilder fullMessage = new StringBuilder();
        Throwable current = thrown;
        while (current != null) {
            if (current.getMessage() != null) {
                fullMessage.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }

        assertThat(fullMessage.toString()).doesNotContain("super secret prompt text");
        assertThat(fullMessage.toString()).doesNotContain("Authorization");
        assertThat(fullMessage.toString()).contains("127.0.0.1:" + port);
    }

    @Test
    void unreachableEndpointFailsFastWithBoundedRetry() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        String baseUrl = "http://127.0.0.1:" + port;

        SpringAiLlmService llm = config.ollamaCodingLlm(baseUrl, "qwen3.6:27b", 45L, 2L, ObservationRegistry.create());

        Instant before = Instant.now();
        assertThrows(Exception.class,
                () -> llm.getModel().call(new Prompt("ping")));
        Instant after = Instant.now();

        Duration elapsed = Duration.between(before, after);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(15));
    }

    @Test
    void optionsConverterMapsAllFieldsAndPassesNullsThrough() {
        LlmOptions fullOptions = LlmOptions.withDefaults()
                .withTemperature(0.5)
                .withTopP(0.9)
                .withTopK(40)
                .withMaxTokens(256)
                .withPresencePenalty(0.1)
                .withFrequencyPenalty(0.2);

        ChatOptions converted = OllamaModelConfig.convertToOllamaOptions(fullOptions, "some-model-tag");

        assertThat(converted.getTemperature()).isEqualTo(0.5);
        assertThat(converted.getTopP()).isEqualTo(0.9);
        assertThat(converted.getTopK()).isEqualTo(40);
        assertThat(converted.getMaxTokens()).isEqualTo(256);
        assertThat(converted.getPresencePenalty()).isEqualTo(0.1);
        assertThat(converted.getFrequencyPenalty()).isEqualTo(0.2);
        assertThat(converted.getModel()).isEqualTo("some-model-tag");

        LlmOptions nullOptions = LlmOptions.withDefaults();

        ChatOptions convertedNulls = assertDoesNotThrow(
                () -> OllamaModelConfig.convertToOllamaOptions(nullOptions, "some-model-tag"));
        assertThat(convertedNulls).isNotNull();
        assertThat(convertedNulls.getTemperature()).isNull();
        assertThat(convertedNulls.getTopP()).isNull();
        assertThat(convertedNulls.getTopK()).isNull();
        assertThat(convertedNulls.getMaxTokens()).isNull();
        assertThat(convertedNulls.getPresencePenalty()).isNull();
        assertThat(convertedNulls.getFrequencyPenalty()).isNull();
    }

    @Test
    void timeoutIsHonouredBehaviorally() throws Exception {
        MockWebServer slowServer = newServer();
        slowServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(VALID_CHAT_RESPONSE_BODY)
                .setBodyDelay(3, TimeUnit.SECONDS));

        SpringAiLlmService shortTimeoutLlm = config.ollamaCodingLlm(
                slowServer.url("/").toString(),
                "my-test-tag",
                45L,
                1L,
                ObservationRegistry.create());

        assertThrows(Exception.class, () -> shortTimeoutLlm.getModel().call(new Prompt("ping")));

        MockWebServer patientServer = newServer();
        patientServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(VALID_CHAT_RESPONSE_BODY)
                .setBodyDelay(3, TimeUnit.SECONDS));

        SpringAiLlmService longTimeoutLlm = config.ollamaCodingLlm(
                patientServer.url("/").toString(),
                "my-test-tag",
                45L,
                30L,
                ObservationRegistry.create());

        ChatResponse response = assertDoesNotThrow(() -> longTimeoutLlm.getModel().call(new Prompt("ping")));
        assertThat(response.getResult().getOutput().getText()).isNotBlank();
    }
}
