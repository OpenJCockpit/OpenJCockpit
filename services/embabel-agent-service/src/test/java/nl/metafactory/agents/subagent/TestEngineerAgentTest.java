package nl.metafactory.agents.subagent;

import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.TestPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestEngineerAgentTest extends SubagentTestBase {

    @Test
    void designReturnsTestPlan() {
        var expected = new TestPlan("id", List.of("test1"), "90%");
        givenAiReturns(TestPlan.class, expected);

        var agent = new TestEngineerAgent(ai);
        var result = agent.design(new RequirementAnalysis("id", List.of("req1"), "summary"));

        assertThat(result).isEqualTo(expected);
    }
}
