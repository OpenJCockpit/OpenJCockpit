package nl.metafactory.agents.subagent;

import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.SpecContent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessAnalystAgentTest extends SubagentTestBase {

    @Test
    void analyzeReturnsRequirementAnalysis() {
        var expected = new RequirementAnalysis("id", List.of("req1"), "summary");
        givenAiReturns(RequirementAnalysis.class, expected);

        var agent = new BusinessAnalystAgent(ai);
        var result = agent.analyze(new SpecContent("id", "file.md", "content", ""));

        assertThat(result).isEqualTo(expected);
    }
}
