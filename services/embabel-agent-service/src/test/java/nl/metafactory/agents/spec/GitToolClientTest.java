package nl.metafactory.agents.spec;

import nl.metafactory.agents.policy.mcp.McpToolInvocation;
import nl.metafactory.agents.policy.mcp.McpToolResult;
import nl.metafactory.agents.policy.mcp.PolicyGuardedMcpToolGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitToolClientTest {

    private static final String PR_URL = "https://github.com/org/repo/pull/7";

    private PolicyGuardedMcpToolGateway gateway;
    private GitToolClient client;

    @BeforeEach
    void setUp() {
        gateway = mock(PolicyGuardedMcpToolGateway.class);
        client = new GitToolClient(gateway);
    }

    @Test
    void callBuildsPolicyGuardedInvocationAndParsesOutcome() {
        when(gateway.invoke(any())).thenReturn(new McpToolResult(true, false,
                "{\"success\":true,\"message\":\"ok\"}", null));

        var outcome = client.call("wf-1", "run-1", "cust", "git_push", Map.of("branch", "spec/x"));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.message()).isEqualTo("ok");
        var captor = ArgumentCaptor.forClass(McpToolInvocation.class);
        verify(gateway).invoke(captor.capture());
        assertThat(captor.getValue().workflowId()).isEqualTo("wf-1");
        assertThat(captor.getValue().workflowExecutionId()).isEqualTo("run-1");
        assertThat(captor.getValue().customerId()).isEqualTo("cust");
        assertThat(captor.getValue().toolName()).isEqualTo("git_push");
        assertThat(captor.getValue().operation()).isEqualTo("spec.publish");
        assertThat(captor.getValue().payload()).containsEntry("branch", "spec/x");
    }

    @Test
    void callInjectsWorkspaceKeyEqualToRunIdWithoutMutatingCallerPayload() {
        when(gateway.invoke(any())).thenReturn(new McpToolResult(true, false,
                "{\"success\":true,\"message\":\"ok\"}", null));

        Map<String, Object> originalPayload = new HashMap<>(Map.of("branch", "spec/x"));

        client.call("wf-1", "run-42", "cust", "git_push", originalPayload);

        var captor = ArgumentCaptor.forClass(McpToolInvocation.class);
        verify(gateway).invoke(captor.capture());
        assertThat(captor.getValue().payload()).containsEntry("workspaceKey", "run-42");
        assertThat(captor.getValue().payload()).containsEntry("branch", "spec/x");
        assertThat(originalPayload).doesNotContainKey("workspaceKey");
        assertThat(originalPayload).hasSize(1);
    }

    @Test
    void outcomeParsesGitToolResultJsonVariants() {
        // A transport error remains an error.
        assertThat(client.outcome(new McpToolResult(false, false, "broken", null)).success()).isFalse();

        // JSON with success=false, also double-encoded.
        var failed = client.outcome(new McpToolResult(true, false,
                "{\"success\":false,\"message\":\"nee\"}", null));
        assertThat(failed.success()).isFalse();
        assertThat(failed.message()).isEqualTo("nee");
        var doubleEncoded = client.outcome(new McpToolResult(true, false,
                "\"{\\\"success\\\":false,\\\"message\\\":\\\"nee\\\"}\"", null));
        assertThat(doubleEncoded.success()).isFalse();

        // JSON without a message field falls back to the raw text; url is carried along.
        var noMessage = client.outcome(new McpToolResult(true, false, "{\"success\":false}", null));
        assertThat(noMessage.success()).isFalse();
        assertThat(noMessage.message()).isEqualTo("{\"success\":false}");
        assertThat(noMessage.url()).isNull();
        var withUrl = client.outcome(new McpToolResult(true, false,
                "{\"success\":true,\"message\":\"ok\",\"url\":\"" + PR_URL + "\"}", null));
        assertThat(withUrl.url()).isEqualTo(PR_URL);

        // Non-JSON, empty or other kinds of JSON content count as success.
        assertThat(client.outcome(new McpToolResult(true, false, "plain text", null)).success()).isTrue();
        assertThat(client.outcome(new McpToolResult(true, false, "", null)).success()).isTrue();
        assertThat(client.outcome(new McpToolResult(true, false, null, null)).success()).isTrue();
        assertThat(client.outcome(new McpToolResult(true, false, "[1,2]", null)).success()).isTrue();
        assertThat(client.outcome(new McpToolResult(true, false, "{\"other\":1}", null)).success()).isTrue();
        assertThat(client.outcome(new McpToolResult(true, false, "{\"success\":\"ja\"}", null)).success()).isTrue();
    }

    @Test
    void outcomeExtractsFileContentAndFileLists() {
        var read = client.outcome(new McpToolResult(true, false,
                "{\"success\":true,\"message\":\"Read a.txt\",\"path\":\"a.txt\",\"content\":\"contents\"}", null));
        assertThat(read.content()).isEqualTo("contents");
        assertThat(read.files()).isNull();

        var listed = client.outcome(new McpToolResult(true, false,
                "{\"success\":true,\"message\":\"Listed 2 files\",\"files\":[\"a.txt\",\"src/B.java\",3]}", null));
        assertThat(listed.files()).containsExactly("a.txt", "src/B.java");
        assertThat(listed.content()).isNull();

        var noFiles = client.outcome(new McpToolResult(true, false,
                "{\"success\":true,\"message\":\"ok\",\"files\":null}", null));
        assertThat(noFiles.files()).isNull();
    }
}
