package nl.metafactory.agents.subagent;

import nl.metafactory.agents.domain.ImplementationPlan;
import nl.metafactory.agents.domain.ReviewReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeadDeveloperAgentTest extends SubagentTestBase {

    @Test
    void refineReturnsRefinedPlan() {
        var expected = new ImplementationPlan("id", List.of("refined-change"), "clean-arch");
        givenAiReturns(ImplementationPlan.class, expected);

        var agent = new LeadDeveloperAgent(ai);
        var result = agent.refine(new ImplementationPlan("id", List.of("change1"), "draft-arch"));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void reviewPlanReturnsReviewReport() {
        var expected = new ReviewReport("id", true, List.of("lgtm"));
        givenAiReturns(ReviewReport.class, expected);

        var agent = new LeadDeveloperAgent(ai);
        var result = agent.reviewPlan(new ImplementationPlan("id", List.of("change1"), "arch"));

        assertThat(result).isEqualTo(expected);
    }
}
