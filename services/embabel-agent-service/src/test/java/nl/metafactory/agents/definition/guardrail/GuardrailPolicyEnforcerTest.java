package nl.metafactory.agents.definition.guardrail;

import com.embabel.agent.api.validation.guardrails.UserInputGuardRail;
import com.embabel.common.core.validation.ValidationSeverity;
import nl.metafactory.agents.definition.model.AgentResolutionException;
import nl.metafactory.agents.definition.model.ResolutionFailure;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuardrailPolicyEnforcerTest {

    static GuardrailExecutor executor(String id, boolean operational, Set<GuardrailScope> scopes) {
        return new GuardrailExecutor() {
            public String guardrailId() {
                return id;
            }

            public String executorName() {
                return "test:" + id;
            }

            public Set<GuardrailScope> scopes() {
                return scopes;
            }

            public boolean operational() {
                return operational;
            }
        };
    }

    @Test
    void bindsOperationalExecutorsAndCollectsEmbabelGuardRails() {
        var enforcer = new GuardrailPolicyEnforcer(List.of(new PiiProtectionGuardrail()), Set.of());
        var binding = enforcer.enforce("customer-support", List.of("pii-protection"));

        assertThat(binding.executors()).containsEntry("pii-protection", "embabel-guardrail:PiiProtectionGuardrail");
        assertThat(binding.guardRails()).hasSize(2);
    }

    @Test
    void twoExecutorsForOneGuardrailFailConstruction() {
        var executors = List.of(executor("pii", true, Set.of(GuardrailScope.LLM_INPUT)), executor("pii", true, Set.of(GuardrailScope.LLM_INPUT)));

        assertThatThrownBy(() -> new GuardrailPolicyEnforcer(executors, Set.of()))
                .isInstanceOf(IllegalStateException.class).hasMessage("duplicate guardrail executor for pii");
    }

    @Test
    void requiredGuardrailWithoutExecutorFailsClosed() {
        var enforcer = new GuardrailPolicyEnforcer(List.of(), Set.of());
        assertThatThrownBy(() -> enforcer.enforce("a", List.of("pii-protection")))
                .isInstanceOf(AgentResolutionException.class)
                .extracting(e -> ((AgentResolutionException) e).failure()).isEqualTo(ResolutionFailure.GUARDRAIL_MISSING);
    }

    @Test
    void nonOperationalExecutorFailsClosed() {
        var enforcer = new GuardrailPolicyEnforcer(
                List.of(executor("tool-policy", false, Set.of(GuardrailScope.TOOL_CALL))), Set.of());
        assertThatThrownBy(() -> enforcer.enforce("a", List.of("tool-policy")))
                .hasMessageContaining("not operational");
    }

    @Test
    void executorWithoutScopeIsNotProtection() {
        var enforcer = new GuardrailPolicyEnforcer(List.of(executor("paper", true, Set.of())), Set.of());
        assertThatThrownBy(() -> enforcer.enforce("a", List.of("paper"))).hasMessageContaining("covers no traffic");
    }

    @Test
    void mandatoryGuardrailCannotBeDroppedByADefinition() {
        var enforcer = new GuardrailPolicyEnforcer(List.of(new PiiProtectionGuardrail()), Set.of("pii-protection"));
        assertThat(enforcer.enforce("a", List.of()).guardrailIds()).containsExactly("pii-protection");

        var missing = new GuardrailPolicyEnforcer(List.of(), Set.of("pii-protection"));
        assertThatThrownBy(() -> missing.enforce("a", List.of())).isInstanceOf(AgentResolutionException.class);
    }

    @Test
    void piiGuardRailBlocksCriticallySoEmbabelAborts() {
        var input = (UserInputGuardRail) new PiiProtectionGuardrail().embabelGuardRails().getFirst();
        var blocked = input.validate("mijn BSN is 111222333", null);
        assertThat(blocked.isValid()).isFalse();
        assertThat(blocked.getErrors().getFirst().getSeverity()).isEqualTo(ValidationSeverity.CRITICAL);
        assertThat(blocked.getErrors().getFirst().getMessage()).doesNotContain("111222333");
        assertThat(input.validate("Wat is mijn saldo?", null).isValid()).isTrue();
    }

    @Test
    void detectorRecognisesIbanAndCardButNotRandomNumbers() {
        assertThat(PiiDetector.detect("IBAN NL91 ABNA 0417 1643 00")).contains("iban");
        assertThat(PiiDetector.detect("card 4111 1111 1111 1111")).contains("payment-card");
        assertThat(PiiDetector.detect("order 123456789 total 42")).isEmpty();
    }
}
