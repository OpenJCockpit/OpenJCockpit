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

@Agent(description = "Log collector subagent that gathers all log entries from the agent workflow execution")
@Component
public class LogCollectorAgent {

    private final Ai ai;

    public LogCollectorAgent(Ai ai) {
        this.ai = ai;
    }

    @Action(description = "Collect log entries from the agent workflow as a log collector")
    @AchievesGoal(description = "Log entries collected and added to the evidence bundle")
    public EvidenceBundle collectLogs(EvidenceBundle bundle) {
        var prompt = "As a log collector, summarize the log entries from this agent workflow execution. " +
                     "Existing entries: " + bundle.entries().size();
        var summary = ai.withLlm(LlmOptions.withDefaultLlm())
                        .createObject(prompt, String.class);
        var logEntry = new EvidenceEntry(Instant.now(), "log-collector", "logs-collected", summary);
        return bundle.withAdditionalEntries(List.of(logEntry));
    }
}
