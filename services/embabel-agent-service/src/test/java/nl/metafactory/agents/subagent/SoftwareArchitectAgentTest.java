package nl.metafactory.agents.subagent;

import nl.metafactory.agents.domain.ImpactReport;
import nl.metafactory.agents.domain.ImplementationPlan;
import nl.metafactory.agents.domain.ReviewReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SoftwareArchitectAgentTest extends SubagentTestBase {

    @Test
    void defineArchitectureReturnsImplementationPlan() {
        var expected = new ImplementationPlan("id", List.of(), "event-driven");
        givenAiReturns(ImplementationPlan.class, expected);

        var agent = new SoftwareArchitectAgent(ai);
        var result = agent.defineArchitecture(new ImpactReport("id", List.of("module-a"), "HIGH"));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void reviewPlanReturnsReviewReport() {
        var expected = new ReviewReport("id", true, List.of("architecture ok"));
        givenAiReturns(ReviewReport.class, expected);

        var agent = new SoftwareArchitectAgent(ai);
        var result = agent.reviewPlan(new ImplementationPlan("id", List.of("change"), "arch"));

        assertThat(result).isEqualTo(expected);
    }
}
