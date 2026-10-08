package nl.metafactory.agents.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import nl.metafactory.agents.domain.ImplementationPlan;
import nl.metafactory.agents.domain.ReviewReport;
import nl.metafactory.agents.domain.TestPlan;
import nl.metafactory.agents.subagent.DeveloperAgent;
import nl.metafactory.agents.subagent.LeadDeveloperAgent;
import nl.metafactory.agents.subagent.SoftwareArchitectAgent;
import org.springframework.stereotype.Component;

/**
 * Calls the same subagent roles as ImplementationAgent but in separate, isolated invocations
 * (no shared blackboard with the implementation phase) to ensure unbiased review.
 */
@Agent(description = "Review agent that performs an isolated, unbiased review of the implementation plan using independent subagent sessions")
@Component
public class ReviewAgent {

    private final SoftwareArchitectAgent softwareArchitectAgent;
    private final DeveloperAgent developerAgent;
    private final LeadDeveloperAgent leadDeveloperAgent;

    public ReviewAgent(SoftwareArchitectAgent softwareArchitectAgent,
                       DeveloperAgent developerAgent,
                       LeadDeveloperAgent leadDeveloperAgent) {
        this.softwareArchitectAgent = softwareArchitectAgent;
        this.developerAgent = developerAgent;
        this.leadDeveloperAgent = leadDeveloperAgent;
    }

    @Action(description = "Review the implementation plan in an isolated session, independently of the implementation process")
    public ReviewReport review(ImplementationPlan plan, TestPlan tests) {
        var archReview = softwareArchitectAgent.reviewPlan(plan);
        var devReview = developerAgent.reviewPlan(plan);
        var leadReview = leadDeveloperAgent.reviewPlan(plan);
        return archReview.aggregate(devReview).aggregate(leadReview);
    }
}
