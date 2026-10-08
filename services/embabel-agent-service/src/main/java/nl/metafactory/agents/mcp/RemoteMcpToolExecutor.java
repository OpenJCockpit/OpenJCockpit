package nl.metafactory.agents.mcp;

import nl.metafactory.agents.policy.mcp.McpToolExecutor;
import nl.metafactory.agents.policy.mcp.McpToolInvocation;
import nl.metafactory.agents.policy.mcp.McpToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The central MCP tool executor: all agent tool calls go through the
 * PolicyGuardedMcpToolGateway to this executor, which routes the call to the
 * first connected MCP server that offers the tool (for example the
 * git-mcp-server for git_checkout_branch, git_create_branch, git_pull,
 * git_commit and git_push).
 */
public class RemoteMcpToolExecutor implements McpToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(RemoteMcpToolExecutor.class);

    private final List<McpServerConnection> connections;

    public RemoteMcpToolExecutor(List<McpServerConnection> connections) {
        this.connections = connections;
    }

    @Override
    public McpToolResult execute(McpToolInvocation invocation) {
        Map<String, Object> arguments = invocation.payload() != null ? invocation.payload() : Map.of();
        for (McpServerConnection connection : connections) {
            Set<String> tools;
            try {
                tools = connection.toolNames();
            } catch (Exception e) {
                log.warn("MCP server {} unreachable during tool discovery: {}", connection.name(), e.getMessage());
                continue;
            }
            if (!tools.contains(invocation.toolName())) {
                continue;
            }
            try {
                return connection.call(invocation.toolName(), arguments);
            } catch (Exception e) {
                log.warn("MCP tool call {} on server {} failed: {}",
                        invocation.toolName(), connection.name(), e.getMessage());
                return new McpToolResult(false, false,
                        "MCP tool call failed on server " + connection.name() + ": " + e.getMessage(), null);
            }
        }
        return new McpToolResult(false, false,
                "MCP tool not available on any connected server: " + invocation.toolName(), null);
    }
}
