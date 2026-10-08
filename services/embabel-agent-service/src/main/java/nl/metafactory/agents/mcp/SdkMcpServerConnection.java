package nl.metafactory.agents.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import nl.metafactory.agents.policy.mcp.McpToolResult;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * McpServerConnection based on the official MCP Java SDK. The connection
 * initializes lazily on first use, so the service also starts when
 * an MCP server is not (yet) reachable.
 */
public class SdkMcpServerConnection implements McpServerConnection {

    private final String name;
    private final McpSyncClient client;
    private volatile boolean initialized;

    public SdkMcpServerConnection(String name, McpSyncClient client) {
        this.name = name;
        this.client = client;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Set<String> toolNames() {
        ensureInitialized();
        return client.listTools().tools().stream()
                .map(McpSchema.Tool::name)
                .collect(Collectors.toSet());
    }

    @Override
    public McpToolResult call(String toolName, Map<String, Object> arguments) {
        ensureInitialized();
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest(toolName, arguments));
        boolean isError = Boolean.TRUE.equals(result.isError());
        return new McpToolResult(!isError, false, textOf(result), result.content());
    }

    private synchronized void ensureInitialized() {
        if (!initialized) {
            client.initialize();
            initialized = true;
        }
    }

    /** Text content of the result; non-text content is ignored. */
    static String textOf(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(content -> ((McpSchema.TextContent) content).text())
                .collect(Collectors.joining("\n"));
    }
}
