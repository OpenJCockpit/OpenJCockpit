package nl.metafactory.agents.definition.runtime;

import nl.metafactory.agents.policy.PolicyDecisionService;
import nl.metafactory.agents.policy.model.PolicyDecision;
import nl.metafactory.agents.policy.model.PolicyDecisionContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpaToolCallAuthorizerTest {

    @Test
    void aToolCallInsideAWorkflowRunIsAttributedToThatWorkflowAndRun() {
        var policy = mock(PolicyDecisionService.class);
        when(policy.canInvokeMcpTool(any())).thenReturn(
                new PolicyDecision(true, "ok", false, "low", List.of(), null, null, null, Instant.now(), false, null));
        // a dashboard run: a workflow, but no git workspace
        var scope = new nl.metafactory.agents.workflowrun.WorkflowRunScope("wf-spec-realise", "run-9", null);
        var run = new RunContext("agent-run-1", "trace-1", "local", "software-developer", "1.1.0", "hash", "svc", scope);

        assertThat(new OpaToolCallAuthorizer(policy).authorize(run, "git.write_file").allowed()).isTrue();

        var context = ArgumentCaptor.forClass(PolicyDecisionContext.class);
        verify(policy).canInvokeMcpTool(context.capture());
        assertThat(context.getValue().workflowId()).isEqualTo("wf-spec-realise");
        assertThat(context.getValue().workflowExecutionId()).isEqualTo("run-9");
        assertThat(context.getValue().customerId()).isEqualTo("local");
        assertThat(context.getValue().mcpToolName()).isEqualTo("git.write_file");
        assertThat(context.getValue().additionalAttributes()).containsEntry("agentRunId", "agent-run-1");
    }

    @Test
    void anAgentRunOutsideAnyWorkflowHasNoWorkflowSoThePolicyCanRefuseIt() {
        var policy = mock(PolicyDecisionService.class);
        when(policy.canInvokeMcpTool(any())).thenReturn(
                new PolicyDecision(false, "workflowId and customerId are required", false, "medium", List.of(),
                        null, null, null, Instant.now(), false, null));
        var run = new RunContext("agent-run-1", "trace-1", "local", "business-analyst", "1.1.0", "hash", "svc");

        assertThat(new OpaToolCallAuthorizer(policy).authorize(run, "git.read_file").allowed()).isFalse();

        var context = ArgumentCaptor.forClass(PolicyDecisionContext.class);
        verify(policy).canInvokeMcpTool(context.capture());
        assertThat(context.getValue().workflowId()).isNull();
    }
}
