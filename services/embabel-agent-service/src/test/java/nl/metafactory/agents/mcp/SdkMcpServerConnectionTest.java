package nl.metafactory.agents.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SdkMcpServerConnectionTest {

    private McpSyncClient client;
    private SdkMcpServerConnection connection;

    @BeforeEach
    void setUp() {
        client = mock(McpSyncClient.class);
        connection = new SdkMcpServerConnection("git", client);
    }

    private McpSchema.Tool tool(String name) {
        return McpSchema.Tool.builder().name(name).description("desc").build();
    }

    @Test
    void nameReturnsConfiguredServerName() {
        assertThat(connection.name()).isEqualTo("git");
    }

    @Test
    void toolNamesInitializesLazilyAndListsTools() {
        when(client.listTools()).thenReturn(new McpSchema.ListToolsResult(
                List.of(tool("git_pull"), tool("git_push")), null));

        assertThat(connection.toolNames()).containsExactlyInAnyOrder("git_pull", "git_push");
        verify(client).initialize();
    }

    @Test
    void initializeHappensOnlyOnceAcrossCalls() {
        when(client.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(tool("git_pull")), null));
        when(client.callTool(any())).thenReturn(new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("ok")), false, null, null));

        connection.toolNames();
        connection.call("git_pull", Map.of());

        verify(client, times(1)).initialize();
    }

    @Test
    void callReturnsSuccessWithJoinedTextContent() {
        when(client.callTool(any())).thenReturn(new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("line 1"), new McpSchema.TextContent("line 2")), false, null, null));

        var result = connection.call("git_pull", Map.of("repositoryUrl", "https://github.com/org/repo.git"));

        assertThat(result.success()).isTrue();
        assertThat(result.blocked()).isFalse();
        assertThat(result.message()).isEqualTo("line 1\nline 2");
        assertThat(result.output()).isNotNull();
    }

    @Test
    void callMapsIsErrorToFailure() {
        when(client.callTool(any())).thenReturn(new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("git_push failed: no write permissions")), true, null, null));

        var result = connection.call("git_push", Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("no write permissions");
    }

    @Test
    void callTreatsNullIsErrorAsSuccess() {
        when(client.callTool(any())).thenReturn(new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("ok")), null, null, null));

        assertThat(connection.call("git_pull", Map.of()).success()).isTrue();
    }

    @Test
    void textOfHandlesNonTextContent() {
        assertThat(SdkMcpServerConnection.textOf(new McpSchema.CallToolResult(
                List.of(new McpSchema.ImageContent(null, "aGk=", "image/png")), false, null, null)))
                .isEmpty();
    }
}
