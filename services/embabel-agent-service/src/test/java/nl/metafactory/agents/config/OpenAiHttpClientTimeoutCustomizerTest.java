package nl.metafactory.agents.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measures — rather than infers — that {@link OpenAiModelConfig#openAiHttpClientBuilderCustomizer}
 * preserves the 45s connect / 600s read timeout budget that previously came from a hand-built
 * {@code ReactorClientHttpRequestFactory}, now that {@link OpenAiModelConfig} consumes Spring AI's
 * auto-configured {@code OpenAiChatModel}/{@code OpenAiEmbeddingModel} beans instead of hand-building
 * the OpenAI client. The customizer is applied to a real {@link SpringAiOpenAiHttpClient.Builder},
 * the client is actually built, and the resulting real OkHttp client's own timeout getters are read
 * back — this is a measured assertion against real objects, not a mock or an inferred value.
 */
class OpenAiHttpClientTimeoutCustomizerTest {

    @Test
    void appliesConfiguredConnectAndReadTimeoutsToTheRealHttpClient() {
        OpenAiModelConfig config = new OpenAiModelConfig();
        OpenAiHttpClientBuilderCustomizer customizer = config.openAiHttpClientBuilderCustomizer(45, 600);

        SpringAiOpenAiHttpClient.Builder builder = SpringAiOpenAiHttpClient.builder();
        customizer.customize(builder);
        SpringAiOpenAiHttpClient httpClient = builder.build();

        assertThat(httpClient.getOkHttpClient().connectTimeoutMillis())
                .as("connect timeout must be 45 seconds")
                .isEqualTo(45_000);
        assertThat(httpClient.getOkHttpClient().readTimeoutMillis())
                .as("read timeout must be 600 seconds")
                .isEqualTo(600_000);
        assertThat(httpClient.getOkHttpClient().callTimeoutMillis())
                .as("overall request/call timeout must be 600 seconds")
                .isEqualTo(600_000);
    }

    @Test
    void appliesDifferentConfiguredValuesCorrectly() {
        OpenAiModelConfig config = new OpenAiModelConfig();
        OpenAiHttpClientBuilderCustomizer customizer = config.openAiHttpClientBuilderCustomizer(10, 120);

        SpringAiOpenAiHttpClient.Builder builder = SpringAiOpenAiHttpClient.builder();
        customizer.customize(builder);
        SpringAiOpenAiHttpClient httpClient = builder.build();

        assertThat(httpClient.getOkHttpClient().connectTimeoutMillis()).isEqualTo(10_000);
        assertThat(httpClient.getOkHttpClient().readTimeoutMillis()).isEqualTo(120_000);
        assertThat(httpClient.getOkHttpClient().callTimeoutMillis()).isEqualTo(120_000);
    }
}
