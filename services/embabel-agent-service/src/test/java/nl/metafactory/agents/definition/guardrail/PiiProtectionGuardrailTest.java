package nl.metafactory.agents.definition.guardrail;

import com.embabel.agent.core.Blackboard;
import com.embabel.common.core.thinking.ThinkingResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PiiProtectionGuardrailTest {

    private final Blackboard blackboard = mock(Blackboard.class);

    @Test
    void bothDirectionsDescribeWhatTheyBlock() {
        assertThat(new PiiProtectionGuardrail.Input().getDescription()).contains("prompts");
        assertThat(new PiiProtectionGuardrail.Output().getDescription()).contains("responses");
    }

    @Test
    void aThinkingResponseIsCheckedOnItsResult() {
        var output = new PiiProtectionGuardrail.Output();

        assertThat(output.validate(new ThinkingResponse<>("BSN 111222333", List.of(), null), blackboard).isValid()).isFalse();
        assertThat(output.validate(new ThinkingResponse<>("Your account is active", List.of(), null), blackboard).isValid()).isTrue();
    }

    @Test
    void detectsCardNumbersWhoseDoubledDigitsExceedNine() {
        assertThat(PiiDetector.detect("card 5555 5555 5555 4444")).containsExactly("payment-card");
        assertThat(PiiDetector.detect(null)).isEmpty();
        assertThat(PiiDetector.detect("")).isEmpty();
        assertThat(PiiDetector.isLuhn("4242")).isFalse();
    }
}
