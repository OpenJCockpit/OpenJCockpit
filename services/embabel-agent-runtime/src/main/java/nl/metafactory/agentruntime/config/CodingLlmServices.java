package nl.metafactory.agentruntime.config;

import com.embabel.agent.spi.support.springai.SpringAiLlmService;
import com.embabel.common.ai.model.OptionsConverter;
import com.embabel.common.ai.model.PricingModel;
import com.embabel.common.ai.prompt.PromptContributor;
import org.springframework.ai.chat.model.ChatModel;

import java.time.LocalDate;
import java.util.List;

/** Shared assembly of the locally hosted coding LLM, whichever route (gateway or direct Ollama) serves it. */
final class CodingLlmServices {

    /**
     * Embabel's {@code SpringAiLlmService} joins its prompt contributors into a single SystemMessage
     * prepended to every call, so the Qwen "no thinking" directive is injected here rather than in each agent.
     */
    static final PromptContributor NO_THINK_PROMPT_CONTRIBUTOR = PromptContributor.fixed("/no_think");

    private CodingLlmServices() {
    }

    static SpringAiLlmService create(String model, String provider, ChatModel chatModel,
                                     OptionsConverter optionsConverter) {
        return new SpringAiLlmService(
                model,
                provider,
                chatModel,
                optionsConverter,
                LocalDate.of(2025, 4, 1), // informational placeholder, not verified against a model card
                List.of(NO_THINK_PROMPT_CONTRIBUTOR),
                PricingModel.usdPer1MTokens(0.0, 0.0));
    }
}
