package nl.metafactory.agents.subagent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import nl.metafactory.agents.domain.EvidenceBundle;
import nl.metafactory.agents.domain.EvidenceEntry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Agent(description = "Action collector subagent that records all actions taken during the agent workflow execution")
@Component
public class ActionCollectorAgent {

    private final Ai ai;

    public ActionCollectorAgent(Ai ai) {
        this.ai = ai;
    }

    @Action(description = "Record all actions taken during the agent workflow as an action collector")
    @AchievesGoal(description = "Actions recorded and added to the evidence bundle")
    public EvidenceBundle collectActions(EvidenceBundle bundle) {
        var prompt = "As an action collector, record all actions taken during this agent workflow. " +
                     "Workflow specId: " + bundle.specId();
        var summary = ai.withLlm(LlmOptions.withDefaultLlm())
                        .createObject(prompt, String.class);
        var actionEntry = new EvidenceEntry(Instant.now(), "action-collector", "actions-recorded", summary);
        return bundle.withAdditionalEntries(List.of(actionEntry));
    }
}
