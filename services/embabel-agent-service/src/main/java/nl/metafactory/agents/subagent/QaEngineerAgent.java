package nl.metafactory.agents.subagent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.TestPlan;
import org.springframework.stereotype.Component;

@Agent(description = "QA engineer subagent that adds acceptance criteria and quality scenarios to the test plan")
@Component
public class QaEngineerAgent {

    private final Ai ai;

    public QaEngineerAgent(Ai ai) {
        this.ai = ai;
    }

    @Action(description = "Add acceptance criteria and QA scenarios as a QA engineer")
    @AchievesGoal(description = "Acceptance criteria and QA scenarios added to the test plan")
    public TestPlan review(RequirementAnalysis requirements) {
        var prompt = "As a QA engineer, add acceptance criteria and edge-case scenarios for the following requirements. " +
                     "Requirements: " + requirements.requirements() + "\nSummary: " + requirements.summary();
        return ai.withLlm(LlmOptions.withDefaultLlm())
                 .createObject(prompt, TestPlan.class);
    }
}
