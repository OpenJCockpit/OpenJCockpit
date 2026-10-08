package nl.metafactory.agents.spec;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.agents.policy.mcp.McpToolInvocation;
import nl.metafactory.agents.policy.mcp.McpToolResult;
import nl.metafactory.agents.policy.mcp.PolicyGuardedMcpToolGateway;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin client for the git tools of the git-mcp-server, on top of the
 * PolicyGuardedMcpToolGateway (so OPA policies remain in effect).
 * MCP success only means the tool call was delivered; the git-mcp-server
 * reports real git errors as result JSON in the text content
 * ({@code success=false} + {@code message}), and provides extra fields such as
 * {@code url} (pull request), {@code content} (git_read_file) and
 * {@code files} (git_list_files). Without this parsing, every failed
 * clone/commit/push would look successful.
 */
@Service
public class GitToolClient {

    private final PolicyGuardedMcpToolGateway gateway;
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    public GitToolClient(PolicyGuardedMcpToolGateway gateway) {
        this.gateway = gateway;
    }

    public GitToolOutcome call(String workflowId, String runId, String customerId,
                               String toolName, Map<String, Object> payload) {
        Map<String, Object> payloadWithWorkspaceKey = new HashMap<>(payload);
        payloadWithWorkspaceKey.put("workspaceKey", runId);
        McpToolResult result = gateway.invoke(new McpToolInvocation(workflowId, runId, null, null,
                customerId, null, "requirement", null, null, toolName, "spec.publish", payloadWithWorkspaceKey));
        return outcome(result);
    }

    GitToolOutcome outcome(McpToolResult result) {
        if (!result.success()) {
            return new GitToolOutcome(false, result.message(), null, null, null);
        }
        JsonNode node = parseJson(result.message());
        if (node != null && node.isObject() && node.path("success").isBoolean()) {
            String message = node.path("message").isTextual()
                    ? node.get("message").asString() : result.message();
            String url = node.path("url").isTextual() ? node.get("url").asString() : null;
            String content = node.path("content").isTextual() ? node.get("content").asString() : null;
            List<String> files = null;
            if (node.path("files").isArray()) {
                files = new ArrayList<>();
                for (JsonNode file : node.get("files")) {
                    if (file.isTextual()) {
                        files.add(file.asString());
                    }
                }
            }
            return new GitToolOutcome(node.get("success").asBoolean(), message, url, content, files);
        }
        return new GitToolOutcome(true, result.message(), null, null, null);
    }

    private JsonNode parseJson(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(message);
            if (node.isTextual()) {
                // Some MCP servers return the result double-encoded.
                node = objectMapper.readTree(node.asString());
            }
            return node;
        } catch (JacksonException e) {
            return null;
        }
    }

    public record GitToolOutcome(boolean success, String message, String url,
                                 String content, List<String> files) {}
}
