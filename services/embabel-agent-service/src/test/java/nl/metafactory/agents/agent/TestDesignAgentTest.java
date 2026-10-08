package nl.metafactory.agents.agent;

import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.TestPlan;
import nl.metafactory.agents.subagent.QaEngineerAgent;
import nl.metafactory.agents.subagent.TestEngineerAgent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestDesignAgentTest {

    @Test
    void designTestsMergesEngineerAndQaPlans() {
        var testEngineerAgent = mock(TestEngineerAgent.class);
        var qaEngineerAgent = mock(QaEngineerAgent.class);
        var requirements = new RequirementAnalysis("id", List.of("req1"), "summary");
        when(testEngineerAgent.design(requirements)).thenReturn(new TestPlan("id", List.of("eng-test"), "80%"));
        when(qaEngineerAgent.review(requirements)).thenReturn(new TestPlan("id", List.of("qa-test"), "90%"));

        var agent = new TestDesignAgent(testEngineerAgent, qaEngineerAgent);
        var result = agent.designTests(requirements);

        assertThat(result.testCases()).containsExactly("eng-test", "qa-test");
    }
}
