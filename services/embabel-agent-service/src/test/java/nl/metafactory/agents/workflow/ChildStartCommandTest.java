package nl.metafactory.agents.workflow;

import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.workflow.model.WorkflowOrbMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChildStartCommandTest {

    private static AgentRunRequest sampleRequest() {
        return new AgentRunRequest("cust", "spec.md", List.of("req"), "user1",
                "https://github.com/org/repo", "bot", "secret", null, "develop", null, null,
                RunInitiator.trigger("test"), List.of("wf-test"));
    }

    @Test
    void accessorsReturnPassedValues() {
        var request = sampleRequest();
        var initiator = RunInitiator.trigger("test");
        var ancestry = List.of("workflow-a");

        var command = new ChildStartCommand("wf-child", request, WorkflowOrbMode.SEQUENTIAL, initiator, ancestry);

        assertThat(command.targetWorkflowId()).isEqualTo("wf-child");
        assertThat(command.parentRequest()).isEqualTo(request);
        assertThat(command.mode()).isEqualTo(WorkflowOrbMode.SEQUENTIAL);
        assertThat(command.initiator()).isEqualTo(initiator);
        assertThat(command.chainAncestry()).containsExactly("workflow-a");
    }

    @Test
    void equalsHashCodeAndToStringBehaveConsistently() {
        var request = sampleRequest();
        var initiator = RunInitiator.trigger("test");
        var ancestry = List.of("workflow-a");

        var a = new ChildStartCommand("wf-child", request, WorkflowOrbMode.SEQUENTIAL, initiator, ancestry);
        var b = new ChildStartCommand("wf-child", request, WorkflowOrbMode.SEQUENTIAL, initiator, ancestry);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).isNotNull();
    }
}
