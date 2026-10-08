package nl.metafactory.agents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties("metafactory.agent-pipeline")
public class AgentPipelineProperties {

    private List<String> sequence = new ArrayList<>(List.of(
            "requirement", "impact", "test-design", "implementation", "review", "realisation", "evidence"
    ));

    public List<String> getSequence() {
        return sequence;
    }

    public void setSequence(List<String> sequence) {
        this.sequence = sequence;
    }
}
