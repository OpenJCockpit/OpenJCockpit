package nl.metafactory.agents.subagent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.SpecContent;
import org.springframework.stereotype.Component;

@Agent(description = "Business analyst subagent that extracts structured requirements from specification documents")
@Component
public class BusinessAnalystAgent {

    private final Ai ai;

    public BusinessAnalystAgent(Ai ai) {
        this.ai = ai;
    }

    @Action(description = "Extract structured requirements from the specification as a business analyst")
    @AchievesGoal(description = "Requirements extracted and structured from the specification")
    public RequirementAnalysis analyze(SpecContent spec) {
        var prompt = "As a business analyst, analyze the following specification and extract structured requirements. " +
                     "Specification file: " + spec.fileName() + "\nContent: " + spec.content();
        return ai.withLlm(LlmOptions.withDefaultLlm())
                 .createObject(prompt, RequirementAnalysis.class);
    }
}
