package nl.metafactory.agents.subagent;

import nl.metafactory.agents.domain.EvidenceBundle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActionCollectorAgentTest extends SubagentTestBase {

    @Test
    void collectActionsAddsActionEntry() {
        givenAiReturns(String.class, "actions summary");
        var base = new EvidenceBundle("id", List.of(), Instant.now());

        var agent = new ActionCollectorAgent(ai);
        var result = agent.collectActions(base);

        assertThat(result.entries()).hasSize(1);
        assertThat(result.entries().get(0).agentId()).isEqualTo("action-collector");
        assertThat(result.entries().get(0).outcome()).isEqualTo("actions summary");
    }
}
