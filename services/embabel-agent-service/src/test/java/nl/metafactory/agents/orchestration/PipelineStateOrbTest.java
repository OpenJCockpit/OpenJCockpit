package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.RunInitiator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineStateOrbTest {

    private PipelineState samplePipelineState() {
        var request = new AgentRunRequest("cust1", "spec.md", List.of("realisation"), "workflow:wf-1",
                "https://github.com/org/repo", null, null, null, null, null, null,
                RunInitiator.trigger("test"), List.of("wf-test"));
        return new PipelineState("run-1", new SpecContent("run-1", "spec.md", "spec.md", ""),
                Set.of("realisation"), request);
    }

    @Test
    void orbDoneIsFalseUntilMarkedCompleted() {
        var state = samplePipelineState();

        assertThat(state.orbDone("orb:0")).isFalse();

        state.markOrbCompleted("orb:0");

        assertThat(state.orbDone("orb:0")).isTrue();
        assertThat(state.orbDone("orb:1")).isFalse();
    }
}
