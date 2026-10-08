package nl.metafactory.agents.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import nl.metafactory.agents.domain.ImpactReport;
import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.SpecContent;
import org.springframework.stereotype.Component;

@Agent(description = "Impact analysis agent that assesses the system-wide impact of the requirements")
@Component
public class ImpactAnalysisAgent {

    private final Ai ai;

    public ImpactAnalysisAgent(Ai ai) {
        this.ai = ai;
    }

    @Action(description = "Analyze the system impact of the identified requirements")
    public ImpactReport analyzeImpact(SpecContent spec, RequirementAnalysis requirements) {
        var prompt = "As an impact analysis expert, assess the system-wide impact of the following requirements. " +
                     "Spec: " + spec.fileName() +
                     "\nRequirements: " + requirements.requirements() +
                     "\nSummary: " + requirements.summary();
        return ai.withLlm(LlmOptions.withDefaultLlm())
                 .createObject(prompt, ImpactReport.class);
    }
}
