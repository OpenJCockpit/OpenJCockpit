package nl.metafactory.agents.agent;

import nl.metafactory.agents.domain.ImplementationPlan;
import nl.metafactory.agents.domain.ReviewReport;
import nl.metafactory.agents.domain.TestPlan;
import nl.metafactory.agents.subagent.DeveloperAgent;
import nl.metafactory.agents.subagent.LeadDeveloperAgent;
import nl.metafactory.agents.subagent.SoftwareArchitectAgent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewAgentTest {

    @Test
    void reviewAggregatesAllSubagentReviews() {
        var architect = mock(SoftwareArchitectAgent.class);
        var developer = mock(DeveloperAgent.class);
        var leadDev = mock(LeadDeveloperAgent.class);

        var plan = new ImplementationPlan("id", List.of("change"), "arch");
        var tests = new TestPlan("id", List.of("test1"), "90%");

        when(architect.reviewPlan(plan)).thenReturn(new ReviewReport("id", true, List.of("arch ok")));
        when(developer.reviewPlan(plan)).thenReturn(new ReviewReport("id", true, List.of("dev ok")));
        when(leadDev.reviewPlan(plan)).thenReturn(new ReviewReport("id", false, List.of("needs refactor")));

        var agent = new ReviewAgent(architect, developer, leadDev);
        var result = agent.review(plan, tests);

        assertThat(result.approved()).isFalse();
        assertThat(result.findings()).containsExactly("arch ok", "dev ok", "needs refactor");
    }
}
