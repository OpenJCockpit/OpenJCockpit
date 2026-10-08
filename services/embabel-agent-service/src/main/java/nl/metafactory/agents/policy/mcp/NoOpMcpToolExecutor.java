package nl.metafactory.agents.policy.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default no-op MCP tool executor, active as long as the central MCP client is
 * disabled (metafactory.mcp.enabled=false). With the client enabled,
 * RemoteMcpToolExecutor takes over and tool calls go to the connected
 * MCP servers (such as the git-mcp-server).
 */
@Component
@ConditionalOnProperty(prefix = "metafactory.mcp", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpMcpToolExecutor implements McpToolExecutor {

    @Override
    public McpToolResult execute(McpToolInvocation invocation) {
        return new McpToolResult(false, false,
                "MCP tool execution not yet implemented: " + invocation.toolName(), null);
    }
}
