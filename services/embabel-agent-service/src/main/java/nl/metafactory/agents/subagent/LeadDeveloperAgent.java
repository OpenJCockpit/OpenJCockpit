package nl.metafactory.agents.subagent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import nl.metafactory.agents.domain.ImplementationPlan;
import nl.metafactory.agents.domain.ReviewReport;
import org.springframework.stereotype.Component;

@Agent(description = "Lead developer subagent that refines and approves the implementation plan")
@Component
public class LeadDeveloperAgent {

    private final Ai ai;

    public LeadDeveloperAgent(Ai ai) {
        this.ai = ai;
    }

    @Action(description = "Refine the merged implementation plan as a lead developer")
    @AchievesGoal(description = "Implementation plan refined and approved by the lead developer")
    public ImplementationPlan refine(ImplementationPlan plan) {
        var prompt = "As a lead developer, review and refine the following implementation plan. " +
                     "Ensure best practices and coherence. Changes: " + plan.proposedChanges() +
                     "\nArchitecture decision: " + plan.architectureDecision();
        return ai.withLlm(LlmOptions.withDefaultLlm())
                 .createObject(prompt, ImplementationPlan.class);
    }

    public ReviewReport reviewPlan(ImplementationPlan plan) {
        var prompt = "As a lead developer, critically review the following implementation plan. " +
                     "Changes: " + plan.proposedChanges() + "\nArchitecture: " + plan.architectureDecision();
        return ai.withLlm(LlmOptions.withDefaultLlm())
                 .createObject(prompt, ReviewReport.class);
    }
}
