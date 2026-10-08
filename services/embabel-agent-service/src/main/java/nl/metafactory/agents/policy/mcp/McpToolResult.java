package nl.metafactory.agents.policy.mcp;

public record McpToolResult(
        boolean success,
        boolean blocked,
        String message,
        Object output
) {

    public static McpToolResult blocked(String reason) {
        return new McpToolResult(false, true, reason, null);
    }
}
