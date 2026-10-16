package nl.metafactory.common.mcp;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * Calls a tool of an MCP server registered with the LiteLLM gateway through LiteLLM's REST bridge
 * {@code POST /mcp-rest/tools/call} ({@code server_id} + upstream tool name + arguments; verified
 * against the pinned v1.101.2 source, {@code proxy/_experimental/mcp_server/rest_endpoints.py}).
 * The caller's LiteLLM virtual key must be granted the server ({@code object_permission.mcp_servers}).
 *
 * <p>Tool arguments and results are never logged: they can contain repository content.
 */
public class LiteLlmMcpToolClient {

    /** Gateway root (e.g. {@code http://litellm:4000}), virtual key and MCP server id. */
    public record Config(String baseUrl, String apiKey, String serverId, Duration timeout) {
        public boolean configured() {
            return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank()
                    && serverId != null && !serverId.isBlank();
        }

        @Override
        public String toString() {
            return "Config[baseUrl=" + baseUrl + ", apiKey=***, serverId=" + serverId + ", timeout=" + timeout + "]";
        }
    }

    /** Outcome of one tool call: the tool's text content and whether the MCP result was an error. */
    public record ToolResult(boolean error, String text) {
    }

    /** The gateway or the MCP server could not be reached, or refused the call. Never carries content. */
    public static class McpCallException extends RuntimeException {
        public McpCallException(String message) {
            super(message);
        }
    }

    private final Config config;
    private final RestClient rest;
    private final JsonMapper json = JsonMapper.builder().build();

    public LiteLlmMcpToolClient(Config config) {
        this(config, defaultRestClient(config.timeout()));
    }

    LiteLlmMcpToolClient(Config config, RestClient rest) {
        this.config = config;
        this.rest = rest;
    }

    static RestClient defaultRestClient(Duration timeout) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(timeout != null ? timeout : Duration.ofSeconds(180));
        return RestClient.builder().requestFactory(factory).build();
    }

    public boolean configured() {
        return config.configured();
    }

    public ToolResult call(String toolName, Map<String, ?> arguments) {
        if (!configured()) {
            throw new McpCallException("LiteLLM MCP gateway is not configured");
        }
        byte[] body;
        try {
            body = rest.post().uri(config.baseUrl().replaceAll("/+$", "") + "/mcp-rest/tools/call")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("server_id", config.serverId(), "name", toolName, "arguments", arguments))
                    .retrieve().body(byte[].class);
        } catch (RestClientResponseException e) {
            throw new McpCallException("MCP tool " + toolName + " via LiteLLM failed with HTTP " + e.getStatusCode().value());
        } catch (RuntimeException e) {
            throw new McpCallException("MCP tool " + toolName + " via LiteLLM is unreachable: " + e.getClass().getSimpleName());
        }
        return parse(body);
    }

    /** MCP {@code CallToolResult}: {@code {"content":[{"type":"text","text":...}], "isError": bool}}. */
    ToolResult parse(byte[] body) {
        JsonNode result = json.readTree(body == null ? new byte[0] : body);
        var text = new StringBuilder();
        for (JsonNode part : result.path("content")) {
            if ("text".equals(part.path("type").asString(""))) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(part.path("text").asString(""));
            }
        }
        return new ToolResult(result.path("isError").asBoolean(false), text.toString());
    }
}
