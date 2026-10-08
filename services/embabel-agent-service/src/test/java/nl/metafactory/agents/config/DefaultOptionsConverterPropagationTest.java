package nl.metafactory.agents.config;

import com.embabel.common.ai.model.DefaultOptionsConverter;
import com.embabel.common.ai.model.LlmOptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R14 (architecture risk): {@code com.embabel.agent.api.models.OpenAiChatOptionsConverter}, the
 * OpenAI-specific {@code OptionsConverter} this module used on Embabel 0.2.0, was removed in
 * Embabel 1.5.1 with no direct replacement; {@link OpenAiModelConfig} now uses the generic
 * {@link DefaultOptionsConverter#INSTANCE} instead. This test measures — rather than assumes —
 * exactly which {@link LlmOptions} fields survive that conversion.
 *
 * <p>Finding: temperature, topP, maxTokens, presencePenalty and frequencyPenalty all survive.
 * {@code topK} does NOT survive — {@link DefaultOptionsConverter} never calls a topK builder
 * method. This is NOT a new regression introduced by this delivery: decompiling the deleted
 * Embabel 0.2.0 {@code OpenAiChatOptionsConverter} shows it never mapped {@code topK} into
 * {@code OpenAiChatOptions} either (correctly, since OpenAI's Chat Completions API has no
 * {@code top_k} request parameter at all — only providers like Ollama/Anthropic honor it). No
 * custom OptionsConverter shim is introduced here because there is nothing for OpenAI to gain
 * from one; this test exists solely to make that fact explicit and permanently regression-tested
 * rather than silently assumed.
 */
class DefaultOptionsConverterPropagationTest {

    @Test
    void propagatesTemperatureTopPMaxTokensPresencePenaltyAndFrequencyPenalty() {
        LlmOptions options = LlmOptions.withDefaults()
                .withTemperature(0.42)
                .withTopP(0.77)
                .withMaxTokens(1234)
                .withPresencePenalty(0.11)
                .withFrequencyPenalty(0.22);

        ChatOptions converted = DefaultOptionsConverter.INSTANCE.convertOptions(options, "gpt-4.1-mini");

        assertThat(converted.getTemperature()).as("temperature must propagate").isEqualTo(0.42);
        assertThat(converted.getTopP()).as("topP must propagate").isEqualTo(0.77);
        assertThat(converted.getMaxTokens()).as("maxTokens must propagate").isEqualTo(1234);
        assertThat(converted.getPresencePenalty()).as("presencePenalty must propagate").isEqualTo(0.11);
        assertThat(converted.getFrequencyPenalty()).as("frequencyPenalty must propagate").isEqualTo(0.22);
        assertThat(converted.getModel()).as("model must propagate").isEqualTo("gpt-4.1-mini");
    }

    @Test
    void doesNotPropagateTopKConfirmingNoRegressionSinceOpenAiNeverSupportedIt() {
        LlmOptions options = LlmOptions.withDefaults().withTopK(50);

        ChatOptions converted = DefaultOptionsConverter.INSTANCE.convertOptions(options, "gpt-4.1-mini");

        assertThat(converted.getTopK())
                .as("topK is measured here to be dropped by DefaultOptionsConverter; this matches "
                        + "the deleted Embabel 0.2.0 OpenAiChatOptionsConverter's behavior exactly, "
                        + "since OpenAI's Chat Completions API has no top_k parameter — not a regression")
                .isNull();
    }
}
