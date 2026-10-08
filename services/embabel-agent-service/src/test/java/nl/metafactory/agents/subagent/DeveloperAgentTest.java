package nl.metafactory.agents.subagent;

import nl.metafactory.agents.domain.ImplementationPlan;
import nl.metafactory.agents.domain.ReviewReport;
import nl.metafactory.agents.domain.SpecContent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeveloperAgentTest extends SubagentTestBase {

    @Test
    void proposeReturnsImplementationPlan() {
        var expected = new ImplementationPlan("id", List.of("change1"), "");
        givenAiReturns(ImplementationPlan.class, expected);

        var agent = new DeveloperAgent(ai);
        var result = agent.propose(new SpecContent("id", "file.md", "content", ""));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void reviewPlanReturnsReviewReport() {
        var expected = new ReviewReport("id", true, List.of());
        givenAiReturns(ReviewReport.class, expected);

        var agent = new DeveloperAgent(ai);
        var result = agent.reviewPlan(new ImplementationPlan("id", List.of("change1"), "arch"));

        assertThat(result).isEqualTo(expected);
    }
}
