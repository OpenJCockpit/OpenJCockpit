package nl.metafactory.agents.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.TestPlan;
import nl.metafactory.agents.subagent.QaEngineerAgent;
import nl.metafactory.agents.subagent.TestEngineerAgent;
import org.springframework.stereotype.Component;

@Agent(description = "Test design agent that designs the test plan using test engineer and QA engineer subagents")
@Component
public class TestDesignAgent {

    private final TestEngineerAgent testEngineerAgent;
    private final QaEngineerAgent qaEngineerAgent;

    public TestDesignAgent(TestEngineerAgent testEngineerAgent, QaEngineerAgent qaEngineerAgent) {
        this.testEngineerAgent = testEngineerAgent;
        this.qaEngineerAgent = qaEngineerAgent;
    }

    @Action(description = "Design the full test plan by combining test engineer cases with QA acceptance criteria")
    public TestPlan designTests(RequirementAnalysis requirements) {
        var engineerPlan = testEngineerAgent.design(requirements);
        var qaPlan = qaEngineerAgent.review(requirements);
        return engineerPlan.merge(qaPlan);
    }
}
