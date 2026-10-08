package nl.metafactory.agents.policy.mcp;

import nl.metafactory.agents.policy.PolicyDecisionService;
import nl.metafactory.agents.policy.model.PolicyDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyGuardedMcpToolGatewayTest {

    private PolicyDecisionService policyDecisionService;
    private McpToolExecutor executor;
    private PolicyGuardedMcpToolGateway gateway;

    @BeforeEach
    void setUp() {
        policyDecisionService = mock(PolicyDecisionService.class);
        executor = mock(McpToolExecutor.class);
        gateway = new PolicyGuardedMcpToolGateway(policyDecisionService, executor);
    }

    private McpToolInvocation invocation() {
        return new McpToolInvocation("wf-1", "exec-1", "project-1", "Project One", "customer-1",
                "Customer One", "requirements-agent", "jira-analysis-subagent", "extract-requirements",
                "jira.createIssue", "create", Map.of("summary", "New requirement"));
    }

    @Test
    void invokesToolWhenAllowed() {
        when(policyDecisionService.canInvokeMcpTool(any())).thenReturn(
                new PolicyDecision(true, "allowed", false, "low", List.of(), "decision-1",
                        "/v1/data/x", null, Instant.now(), false, null));
        when(executor.execute(any())).thenReturn(new McpToolResult(true, false, "ok", "result"));

        var result = gateway.invoke(invocation());

        assertThat(result.success()).isTrue();
        assertThat(result.blocked()).isFalse();
        verify(executor).execute(invocation());
    }

    @Test
    void blocksToolAndNeverExecutesWhenDenied() {
        when(policyDecisionService.canInvokeMcpTool(any())).thenReturn(
                new PolicyDecision(false, "requires human approval", true, "high",
                        List.of("agent-tool-governance"), "decision-2", "/v1/data/x", null, Instant.now(), false, null));

        var result = gateway.invoke(invocation());

        assertThat(result.success()).isFalse();
        assertThat(result.blocked()).isTrue();
        assertThat(result.message()).isEqualTo("requires human approval");
        verify(executor, never()).execute(any());
    }

    @Test
    void usesFallbackMessageWhenDenialReasonIsNull() {
        when(policyDecisionService.canInvokeMcpTool(any())).thenReturn(
                new PolicyDecision(false, null, false, "unknown", List.of(), null, null, null, Instant.now(), true, "FAIL_CLOSED"));

        var result = gateway.invoke(invocation());

        assertThat(result.blocked()).isTrue();
        assertThat(result.message()).isEqualTo("Blocked by policy");
    }

    @Test
    void passesToolIdentifiersIntoPolicyContext() {
        when(policyDecisionService.canInvokeMcpTool(any())).thenReturn(
                new PolicyDecision(true, "allowed", false, "low", List.of(), "decision-1", null, null, Instant.now(), false, null));
        when(executor.execute(any())).thenReturn(new McpToolResult(true, false, "ok", null));

        gateway.invoke(invocation());

        var captor = org.mockito.ArgumentCaptor.forClass(nl.metafactory.agents.policy.model.PolicyDecisionContext.class);
        verify(policyDecisionService).canInvokeMcpTool(captor.capture());
        var context = captor.getValue();
        assertThat(context.workflowId()).isEqualTo("wf-1");
        assertThat(context.workflowExecutionId()).isEqualTo("exec-1");
        assertThat(context.agentId()).isEqualTo("requirements-agent");
        assertThat(context.subagentId()).isEqualTo("jira-analysis-subagent");
        assertThat(context.skillId()).isEqualTo("extract-requirements");
        assertThat(context.mcpToolName()).isEqualTo("jira.createIssue");
        assertThat(context.mcpToolOperation()).isEqualTo("create");
        assertThat(context.action()).isEqualTo("mcp.tool.invoke");
    }
}
