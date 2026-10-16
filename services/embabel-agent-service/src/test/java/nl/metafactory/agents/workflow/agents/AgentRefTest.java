package nl.metafactory.agents.workflow.agents;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRefTest {

    @Test
    void parsesAndRendersBothSources() {
        assertThat(AgentRef.parse("local:triage")).contains(AgentRef.local("triage"));
        assertThat(AgentRef.parse("a2a:litellm:qa-engineer")).contains(AgentRef.a2a("litellm", "qa-engineer"));
        assertThat(AgentRef.a2a("litellm", "qa-engineer").key()).isEqualTo("a2a:litellm:qa-engineer");
        assertThat(AgentRef.local("triage").key()).isEqualTo("local:triage");
    }

    @Test
    void builtInStageIdsAndMalformedKeysAreNotReferences() {
        for (String key : new String[]{"requirement", "realisation", "local:", "a2a:litellm", "a2a::x", "local:../x",
                "LOCAL:triage", "a2a:lite llm:x", "local:a:b", null}) {
            assertThat(AgentRef.isRef(key)).as(String.valueOf(key)).isFalse();
        }
    }

    @Test
    void providerOnlyForA2A() {
        assertThatThrownBy(() -> new AgentRef(AgentRef.Source.LOCAL, "litellm", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentRef(AgentRef.Source.A2A, null, "x")).isInstanceOf(IllegalArgumentException.class);
    }
}
