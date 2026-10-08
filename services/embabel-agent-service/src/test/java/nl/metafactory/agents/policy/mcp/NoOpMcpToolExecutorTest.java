package nl.metafactory.agents.policy.mcp;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpMcpToolExecutorTest {

    @Test
    void returnsNotImplementedResultWithoutThrowing() {
        var executor = new NoOpMcpToolExecutor();
        var invocation = new McpToolInvocation("wf-1", "exec-1", null, null, null, null,
                "agent-1", null, null, "jira.createIssue", "create", Map.of());

        var result = executor.execute(invocation);

        assertThat(result.success()).isFalse();
        assertThat(result.blocked()).isFalse();
        assertThat(result.message()).contains("jira.createIssue");
    }
}
