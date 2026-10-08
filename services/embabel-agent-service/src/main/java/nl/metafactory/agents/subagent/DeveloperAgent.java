package nl.metafactory.agents.subagent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import nl.metafactory.agents.domain.ImplementationPlan;
import nl.metafactory.agents.domain.ReviewReport;
import nl.metafactory.agents.domain.SpecContent;
import org.springframework.stereotype.Component;

@Agent(description = "Developer subagent that proposes concrete code changes for a specification")
@Component
public class DeveloperAgent {

    private final Ai ai;

    public DeveloperAgent(Ai ai) {
        this.ai = ai;
    }

    @Action(description = "Propose concrete code changes for the specification as a developer")
    @AchievesGoal(description = "Concrete code changes proposed for the specification")
    public ImplementationPlan propose(SpecContent spec) {
        var prompt = "As a developer, propose concrete code changes for the following specification. " +
                     "Specification: " + spec.fileName() + "\nContent: " + spec.content();
        return ai.withLlm(LlmOptions.withDefaultLlm())
                 .createObject(prompt, ImplementationPlan.class);
    }

    public ReviewReport reviewPlan(ImplementationPlan plan) {
        var prompt = "As a developer, review the following implementation plan for correctness and completeness. " +
                     "Changes: " + plan.proposedChanges() + "\nArchitecture: " + plan.architectureDecision();
        return ai.withLlm(LlmOptions.withDefaultLlm())
                 .createObject(prompt, ReviewReport.class);
    }
}
