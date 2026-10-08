package nl.metafactory.agents.agent;

import nl.metafactory.agents.domain.ImpactReport;
import nl.metafactory.agents.domain.ImplementationPlan;
import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.subagent.DeveloperAgent;
import nl.metafactory.agents.subagent.LeadDeveloperAgent;
import nl.metafactory.agents.subagent.SoftwareArchitectAgent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImplementationAgentTest {

    @Test
    void planMergesArchitectAndDevThenRefines() {
        var architect = mock(SoftwareArchitectAgent.class);
        var developer = mock(DeveloperAgent.class);
        var leadDev = mock(LeadDeveloperAgent.class);

        var impact = new ImpactReport("id", List.of("m1"), "LOW");
        var spec = new SpecContent("id", "file.md", "content", "");
        var archPlan = new ImplementationPlan("id", List.of("arch-change"), "event-driven");
        var devPlan = new ImplementationPlan("id", List.of("dev-change"), "");
        var refined = new ImplementationPlan("id", List.of("arch-change", "dev-change"), "event-driven");

        when(architect.defineArchitecture(impact)).thenReturn(archPlan);
        when(developer.propose(spec)).thenReturn(devPlan);
        when(leadDev.refine(any())).thenReturn(refined);

        var agent = new ImplementationAgent(architect, developer, leadDev);
        var result = agent.plan(spec, impact);

        assertThat(result.proposedChanges()).containsExactly("arch-change", "dev-change");
        assertThat(result.architectureDecision()).isEqualTo("event-driven");
    }
}
