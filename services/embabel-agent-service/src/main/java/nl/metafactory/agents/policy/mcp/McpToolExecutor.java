package nl.metafactory.agents.policy.mcp;

/**
 * Actual MCP tool execution seam. There is no real MCP tool invocation wired up anywhere in this
 * service yet (Spring AI's MCP client support is on the classpath but disabled) — this interface
 * exists so a real implementation can be dropped in later without touching
 * PolicyGuardedMcpToolGateway or any caller.
 */
public interface McpToolExecutor {

    McpToolResult execute(McpToolInvocation invocation);
}
