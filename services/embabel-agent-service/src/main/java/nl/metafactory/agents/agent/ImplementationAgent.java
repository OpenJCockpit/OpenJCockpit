package nl.metafactory.agents.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import nl.metafactory.agents.domain.ImpactReport;
import nl.metafactory.agents.domain.ImplementationPlan;
import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.subagent.DeveloperAgent;
import nl.metafactory.agents.subagent.LeadDeveloperAgent;
import nl.metafactory.agents.subagent.SoftwareArchitectAgent;
import org.springframework.stereotype.Component;

@Agent(description = "Implementation agent that creates the implementation plan using architect, developer, and lead developer subagents")
@Component
public class ImplementationAgent {

    private final SoftwareArchitectAgent softwareArchitectAgent;
    private final DeveloperAgent developerAgent;
    private final LeadDeveloperAgent leadDeveloperAgent;

    public ImplementationAgent(SoftwareArchitectAgent softwareArchitectAgent,
                               DeveloperAgent developerAgent,
                               LeadDeveloperAgent leadDeveloperAgent) {
        this.softwareArchitectAgent = softwareArchitectAgent;
        this.developerAgent = developerAgent;
        this.leadDeveloperAgent = leadDeveloperAgent;
    }

    @Action(description = "Create the implementation plan by combining architecture decisions, developer proposals, and lead review")
    public ImplementationPlan plan(SpecContent spec, ImpactReport impact) {
        var architectPlan = softwareArchitectAgent.defineArchitecture(impact);
        var devPlan = developerAgent.propose(spec);
        var merged = architectPlan.merge(devPlan);
        return leadDeveloperAgent.refine(merged);
    }
}
