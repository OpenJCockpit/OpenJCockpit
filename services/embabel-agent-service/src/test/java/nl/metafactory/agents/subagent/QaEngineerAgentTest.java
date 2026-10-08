package nl.metafactory.agents.subagent;

import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.TestPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QaEngineerAgentTest extends SubagentTestBase {

    @Test
    void reviewReturnsTestPlan() {
        var expected = new TestPlan("id", List.of("qa-test1"), "95%");
        givenAiReturns(TestPlan.class, expected);

        var agent = new QaEngineerAgent(ai);
        var result = agent.review(new RequirementAnalysis("id", List.of("req1"), "summary"));

        assertThat(result).isEqualTo(expected);
    }
}
