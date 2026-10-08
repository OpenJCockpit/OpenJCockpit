package nl.metafactory.agents.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;

import java.time.Duration;
import java.util.List;

/**
 * Builds a connection per configured MCP server. Connections are only
 * constructed here (no network contact is made yet) — initialization
 * happens lazily in SdkMcpServerConnection on first tool use.
 */
public class McpConnectionFactory {

    private final McpClientProperties properties;

    public McpConnectionFactory(McpClientProperties properties) {
        this.properties = properties;
    }

    public List<McpServerConnection> connections() {
        return properties.getServers().stream()
                .<McpServerConnection>map(server -> new SdkMcpServerConnection(server.getName(),
                        McpClient.sync(HttpClientSseClientTransport.builder(server.getUrl()).build())
                                .requestTimeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                                .build()))
                .toList();
    }
}
