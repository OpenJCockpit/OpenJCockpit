package nl.metafactory.agents.mcp;

import nl.metafactory.agents.policy.mcp.McpToolInvocation;
import nl.metafactory.agents.policy.mcp.McpToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteMcpToolExecutorTest {

    private McpToolInvocation invocation(String toolName, Map<String, Object> payload) {
        return new McpToolInvocation("wf-1", "run-1", "p-1", "Test Project", "c-1", "Customer",
                "requirement", null, null, toolName, "invoke", payload);
    }

    private McpServerConnection connection(String name, Set<String> tools, McpToolResult result) {
        return new McpServerConnection() {
            @Override public String name() { return name; }
            @Override public Set<String> toolNames() { return tools; }
            @Override public McpToolResult call(String toolName, Map<String, Object> arguments) { return result; }
        };
    }

    @Test
    void routesCallToTheFirstServerThatExposesTheTool() {
        var gitResult = new McpToolResult(true, false, "Checked out branch main", null);
        var executor = new RemoteMcpToolExecutor(List.of(
                connection("other", Set.of("jira_create_issue"), new McpToolResult(false, false, "wrong", null)),
                connection("git", Set.of("git_checkout_branch", "git_push"), gitResult)));

        var result = executor.execute(invocation("git_checkout_branch",
                Map.of("repositoryUrl", "https://github.com/org/repo.git", "branch", "main")));

        assertThat(result).isSameAs(gitResult);
    }

    @Test
    void returnsFailureWhenNoServerExposesTheTool() {
        var executor = new RemoteMcpToolExecutor(List.of(
                connection("git", Set.of("git_pull"), new McpToolResult(true, false, "ok", null))));

        var result = executor.execute(invocation("unknown_tool", Map.of()));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("not available on any connected server").contains("unknown_tool");
    }

    @Test
    void skipsUnreachableServersDuringToolDiscovery() {
        var unreachable = new McpServerConnection() {
            @Override public String name() { return "down"; }
            @Override public Set<String> toolNames() { throw new IllegalStateException("connection refused"); }
            @Override public McpToolResult call(String toolName, Map<String, Object> arguments) {
                throw new IllegalStateException("never called");
            }
        };
        var gitResult = new McpToolResult(true, false, "ok", null);
        var executor = new RemoteMcpToolExecutor(List.of(
                unreachable, connection("git", Set.of("git_pull"), gitResult)));

        assertThat(executor.execute(invocation("git_pull", Map.of()))).isSameAs(gitResult);
    }

    @Test
    void mapsCallExceptionToFailureResult() {
        var broken = new McpServerConnection() {
            @Override public String name() { return "git"; }
            @Override public Set<String> toolNames() { return Set.of("git_push"); }
            @Override public McpToolResult call(String toolName, Map<String, Object> arguments) {
                throw new IllegalStateException("timeout");
            }
        };
        var executor = new RemoteMcpToolExecutor(List.of(broken));

        var result = executor.execute(invocation("git_push", Map.of()));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("failed on server git").contains("timeout");
    }

    @Test
    void nullPayloadBecomesEmptyArguments() {
        var recording = new McpServerConnection() {
            Map<String, Object> seen;
            @Override public String name() { return "git"; }
            @Override public Set<String> toolNames() { return Set.of("git_pull"); }
            @Override public McpToolResult call(String toolName, Map<String, Object> arguments) {
                seen = arguments;
                return new McpToolResult(true, false, "ok", null);
            }
        };
        new RemoteMcpToolExecutor(List.of(recording)).execute(invocation("git_pull", null));

        assertThat(recording.seen).isEmpty();
    }
}
