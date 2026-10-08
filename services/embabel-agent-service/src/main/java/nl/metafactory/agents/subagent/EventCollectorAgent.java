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

@Agent(description = "Event collector subagent that captures all events and state transitions during the agent workflow")
@Component
public class EventCollectorAgent {

    private final Ai ai;

    public EventCollectorAgent(Ai ai) {
        this.ai = ai;
    }

    @Action(description = "Capture all events and state transitions during the agent workflow as an event collector")
    @AchievesGoal(description = "Events and state transitions captured and added to the evidence bundle")
    public EvidenceBundle collectEvents(EvidenceBundle bundle) {
        var prompt = "As an event collector, capture all state transitions and events during this agent workflow. " +
                     "Workflow completed at: " + bundle.completedAt();
        var summary = ai.withLlm(LlmOptions.withDefaultLlm())
                        .createObject(prompt, String.class);
        var eventEntry = new EvidenceEntry(Instant.now(), "event-collector", "events-captured", summary);
        return bundle.withAdditionalEntries(List.of(eventEntry));
    }
}
