package nl.metafactory.agents.mcp;

import nl.metafactory.agents.policy.mcp.McpToolResult;

import java.util.Map;
import java.util.Set;

/**
 * Connection to a single MCP server. The RemoteMcpToolExecutor routes tool calls
 * over these connections; the SDK details stay behind this interface so that
 * the routing can be tested without a network.
 */
public interface McpServerConnection {

    String name();

    Set<String> toolNames();

    McpToolResult call(String toolName, Map<String, Object> arguments);
}
