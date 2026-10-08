package nl.metafactory.agents.subagent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.TestPlan;
import org.springframework.stereotype.Component;

@Agent(description = "Test engineer subagent that designs technical test cases from requirements")
@Component
public class TestEngineerAgent {

    private final Ai ai;

    public TestEngineerAgent(Ai ai) {
        this.ai = ai;
    }

    @Action(description = "Design technical test cases from requirements as a test engineer")
    @AchievesGoal(description = "Technical test cases designed for the requirements")
    public TestPlan design(RequirementAnalysis requirements) {
        var prompt = "As a test engineer, design technical test cases for the following requirements. " +
                     "Requirements: " + requirements.requirements() + "\nSummary: " + requirements.summary();
        return ai.withLlm(LlmOptions.withDefaultLlm())
                 .createObject(prompt, TestPlan.class);
    }
}
