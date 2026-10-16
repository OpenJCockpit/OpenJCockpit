package nl.metafactory.agents.definition.guardrail;

import com.embabel.agent.api.validation.guardrails.AssistantMessageGuardRail;
import com.embabel.agent.api.validation.guardrails.GuardRail;
import com.embabel.agent.api.validation.guardrails.UserInputGuardRail;
import com.embabel.agent.core.Blackboard;
import com.embabel.common.core.thinking.ThinkingResponse;
import com.embabel.common.core.validation.ValidationError;
import com.embabel.common.core.validation.ValidationResult;
import com.embabel.common.core.validation.ValidationSeverity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * {@code pii-protection}: blocks (CRITICAL) any LLM prompt or response containing a BSN, IBAN or
 * payment card number, both before the model sees it and before the caller does.
 */
@Component
public class PiiProtectionGuardrail implements GuardrailExecutor {

    public static final String ID = "pii-protection";

    @Override
    public String guardrailId() {
        return ID;
    }

    @Override
    public String executorName() {
        return "embabel-guardrail:" + PiiProtectionGuardrail.class.getSimpleName();
    }

    @Override
    public Set<GuardrailScope> scopes() {
        return Set.of(GuardrailScope.LLM_INPUT, GuardrailScope.LLM_OUTPUT);
    }

    @Override
    public boolean operational() {
        return true;
    }

    @Override
    public List<GuardRail> embabelGuardRails() {
        return List.of(new Input(), new Output());
    }

    static ValidationResult check(String text) {
        var kinds = PiiDetector.detect(text);
        if (kinds.isEmpty()) {
            return new ValidationResult(true, List.of());
        }
        return new ValidationResult(false, List.of(new ValidationError("pii-detected",
                "high-risk identifier detected: " + String.join(",", kinds), ValidationSeverity.CRITICAL)));
    }

    public static final class Input implements UserInputGuardRail {
        @Override
        public String getName() {
            return ID + ":input";
        }

        @Override
        public String getDescription() {
            return "Blocks prompts containing BSN, IBAN or payment card numbers";
        }

        @Override
        public ValidationResult validate(String input, Blackboard blackboard) {
            return check(input);
        }
    }

    public static final class Output implements AssistantMessageGuardRail {
        @Override
        public String getName() {
            return ID + ":output";
        }

        @Override
        public String getDescription() {
            return "Blocks responses containing BSN, IBAN or payment card numbers";
        }

        @Override
        public ValidationResult validate(String output, Blackboard blackboard) {
            return check(output);
        }

        @Override
        public ValidationResult validate(ThinkingResponse<?> response, Blackboard blackboard) {
            return check(String.valueOf(response.getResult()));
        }
    }
}
