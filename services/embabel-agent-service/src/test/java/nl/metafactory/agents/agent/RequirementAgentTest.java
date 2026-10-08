package nl.metafactory.agents.agent;

import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.subagent.BusinessAnalystAgent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequirementAgentTest {

    @Test
    void analyzeRequirementsDelegatesToBusinessAnalyst() {
        var businessAnalystAgent = mock(BusinessAnalystAgent.class);
        var spec = new SpecContent("id", "file.md", "content", "");
        var expected = new RequirementAnalysis("id", List.of("req1"), "summary");
        when(businessAnalystAgent.analyze(spec)).thenReturn(expected);

        var agent = new RequirementAgent(businessAnalystAgent);
        var result = agent.analyzeRequirements(spec);

        assertThat(result).isEqualTo(expected);
    }
}
