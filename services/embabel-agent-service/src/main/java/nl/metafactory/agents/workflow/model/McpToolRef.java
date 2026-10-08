package nl.metafactory.agents.workflow.model;

/**
 * Placeholder reference to an MCP-connected tool. No MCP client/execution is wired up yet
 * (Spring AI's MCP client support is on the classpath but disabled via spring.ai.mcp.client.enabled=false) —
 * this only carries descriptive metadata so agents/subagents/skills can declare intended tool usage today,
 * ready to be resolved against a real MCP client later.
 */
public record McpToolRef(String name, String description) {
}
