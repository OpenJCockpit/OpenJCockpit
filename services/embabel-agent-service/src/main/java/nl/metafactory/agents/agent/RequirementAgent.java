package nl.metafactory.agents.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.AchievesGoal;
import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.subagent.BusinessAnalystAgent;
import org.springframework.stereotype.Component;

@Agent(description = "Requirements agent that extracts and validates requirements by delegating to business analyst subagents")
@Component
public class RequirementAgent {

    private final BusinessAnalystAgent businessAnalystAgent;

    public RequirementAgent(BusinessAnalystAgent businessAnalystAgent) {
        this.businessAnalystAgent = businessAnalystAgent;
    }

    @Action(description = "Extract and validate requirements from the specification via business analyst subagents")
    @AchievesGoal(description = "Requirements validated and ready for downstream agents")
    public RequirementAnalysis analyzeRequirements(SpecContent spec) {
        return businessAnalystAgent.analyze(spec);
    }
}
