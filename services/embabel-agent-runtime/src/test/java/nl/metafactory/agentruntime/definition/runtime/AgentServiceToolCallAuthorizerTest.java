package nl.metafactory.agentruntime.definition.runtime;

import nl.metafactory.agentruntime.management.AgentServiceClient;
import nl.metafactory.common.agentruntime.ToolInvocationDecision;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceToolCallAuthorizerTest {

    @Test
    void aToolCallInsideAWorkflowRunIsAttributedToThatWorkflowAndRun() {
        var agentService = mock(AgentServiceClient.class);
        when(agentService.decideToolInvocation(any())).thenReturn(new ToolInvocationDecision(true, "ok"));
        // a dashboard run: a workflow, but no git workspace
        var scope = new nl.metafactory.agentruntime.workflowrun.WorkflowRunScope("wf-spec-realise", "run-9", null);
        var run = new RunContext("agent-run-1", "trace-1", "local", "software-developer", "1.1.0", "hash", "svc", scope);

        assertThat(new AgentServiceToolCallAuthorizer(agentService).authorize(run, "git.write_file").allowed()).isTrue();

        var request = ArgumentCaptor.forClass(ToolInvocationDecision.Request.class);
        verify(agentService).decideToolInvocation(request.capture());
        assertThat(request.getValue().workflowId()).isEqualTo("wf-spec-realise");
        assertThat(request.getValue().workflowRunId()).isEqualTo("run-9");
        assertThat(request.getValue().tenantId()).isEqualTo("local");
        assertThat(request.getValue().agentId()).isEqualTo("software-developer");
        assertThat(request.getValue().toolId()).isEqualTo("git.write_file");
        assertThat(request.getValue().principal()).isEqualTo("svc");
        assertThat(request.getValue().attributes()).containsEntry("agentRunId", "agent-run-1")
                .containsEntry("traceId", "trace-1").containsEntry("agentVersion", "1.1.0");
    }

    @Test
    void anAgentRunOutsideAnyWorkflowHasNoWorkflowSoThePolicyCanRefuseIt() {
        var agentService = mock(AgentServiceClient.class);
        when(agentService.decideToolInvocation(any()))
                .thenReturn(new ToolInvocationDecision(false, "workflowId and customerId are required"));
        var run = new RunContext("agent-run-1", "trace-1", "local", "business-analyst", "1.1.0", "hash", "svc");

        var decision = new AgentServiceToolCallAuthorizer(agentService).authorize(run, "git.read_file");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("workflowId and customerId are required");
        var request = ArgumentCaptor.forClass(ToolInvocationDecision.Request.class);
        verify(agentService).decideToolInvocation(request.capture());
        assertThat(request.getValue().workflowId()).isNull();
        assertThat(request.getValue().workflowRunId()).isEqualTo("agent-run-1");
    }

    @Test
    void noDecisionFromTheAgentServiceDeniesTheCall() {
        var agentService = mock(AgentServiceClient.class);
        when(agentService.decideToolInvocation(any()))
                .thenThrow(new AgentServiceClient.ManagementUnavailableException("down", null));
        var run = new RunContext("agent-run-1", "trace-1", "local", "business-analyst", "1.1.0", "hash", "svc");

        var decision = new AgentServiceToolCallAuthorizer(agentService).authorize(run, "git.read_file");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("policy decision unavailable");
    }
}
