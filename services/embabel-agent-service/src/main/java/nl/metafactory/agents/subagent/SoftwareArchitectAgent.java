package nl.metafactory.agents.subagent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import nl.metafactory.agents.domain.ImpactReport;
import nl.metafactory.agents.domain.ImplementationPlan;
import nl.metafactory.agents.domain.ReviewReport;
import org.springframework.stereotype.Component;

@Agent(description = "Software architect subagent that defines architecture decisions for the implementation")
@Component
public class SoftwareArchitectAgent {

    private final Ai ai;

    public SoftwareArchitectAgent(Ai ai) {
        this.ai = ai;
    }

    @Action(description = "Define architecture decision based on impact analysis as a software architect")
    @AchievesGoal(description = "Architecture decision defined based on the impact analysis")
    public ImplementationPlan defineArchitecture(ImpactReport impact) {
        var prompt = "As a software architect, define an architecture decision addressing the following impact report. " +
                     "Affected areas: " + impact.affectedAreas() + "\nRisk level: " + impact.riskLevel();
        return ai.withLlm(LlmOptions.withDefaultLlm())
                 .createObject(prompt, ImplementationPlan.class);
    }

    public ReviewReport reviewPlan(ImplementationPlan plan) {
        var prompt = "As a software architect, review the following implementation plan for architectural soundness. " +
                     "Changes: " + plan.proposedChanges() + "\nArchitecture: " + plan.architectureDecision();
        return ai.withLlm(LlmOptions.withDefaultLlm())
                 .createObject(prompt, ReviewReport.class);
    }
}
