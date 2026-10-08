package nl.metafactory.agents.subagent;

import nl.metafactory.agents.domain.EvidenceBundle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogCollectorAgentTest extends SubagentTestBase {

    @Test
    void collectLogsAddsLogEntry() {
        givenAiReturns(String.class, "log summary");
        var base = new EvidenceBundle("id", List.of(), Instant.now());

        var agent = new LogCollectorAgent(ai);
        var result = agent.collectLogs(base);

        assertThat(result.entries()).hasSize(1);
        assertThat(result.entries().get(0).agentId()).isEqualTo("log-collector");
        assertThat(result.entries().get(0).outcome()).isEqualTo("log summary");
    }
}
