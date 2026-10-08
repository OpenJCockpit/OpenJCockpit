package nl.metafactory.agents.subagent;

import nl.metafactory.agents.domain.EvidenceBundle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventCollectorAgentTest extends SubagentTestBase {

    @Test
    void collectEventsAddsEventEntry() {
        givenAiReturns(String.class, "events summary");
        var base = new EvidenceBundle("id", List.of(), Instant.now());

        var agent = new EventCollectorAgent(ai);
        var result = agent.collectEvents(base);

        assertThat(result.entries()).hasSize(1);
        assertThat(result.entries().get(0).agentId()).isEqualTo("event-collector");
        assertThat(result.entries().get(0).outcome()).isEqualTo("events summary");
    }
}
