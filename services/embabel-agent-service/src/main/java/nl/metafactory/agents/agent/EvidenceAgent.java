package nl.metafactory.agents.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.AchievesGoal;
import nl.metafactory.agents.domain.EvidenceBundle;
import nl.metafactory.agents.domain.ImpactReport;
import nl.metafactory.agents.domain.ImplementationPlan;
import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.ReviewReport;
import nl.metafactory.agents.domain.TestPlan;
import nl.metafactory.agents.subagent.ActionCollectorAgent;
import nl.metafactory.agents.subagent.EventCollectorAgent;
import nl.metafactory.agents.subagent.LogCollectorAgent;
import org.springframework.stereotype.Component;

@Agent(description = "Evidence agent that compiles a full audit bundle from all agent outputs using log, action, and event collector subagents")
@Component
public class EvidenceAgent {

    private final LogCollectorAgent logCollectorAgent;
    private final ActionCollectorAgent actionCollectorAgent;
    private final EventCollectorAgent eventCollectorAgent;

    public EvidenceAgent(LogCollectorAgent logCollectorAgent,
                         ActionCollectorAgent actionCollectorAgent,
                         EventCollectorAgent eventCollectorAgent) {
        this.logCollectorAgent = logCollectorAgent;
        this.actionCollectorAgent = actionCollectorAgent;
        this.eventCollectorAgent = eventCollectorAgent;
    }

    @Action(description = "Compile the full evidence bundle from all agent outputs and enrich it with logs, actions, and events")
    @AchievesGoal(description = "Full evidence bundle compiled from all agent outputs")
    public EvidenceBundle compileEvidence(RequirementAnalysis ra, ImpactReport ir,
                                          TestPlan tp, ImplementationPlan ip, ReviewReport rr) {
        var base = EvidenceBundle.from(ra, ir, tp, ip, rr);
        var withLogs = logCollectorAgent.collectLogs(base);
        var withActions = actionCollectorAgent.collectActions(withLogs);
        return eventCollectorAgent.collectEvents(withActions);
    }
}
