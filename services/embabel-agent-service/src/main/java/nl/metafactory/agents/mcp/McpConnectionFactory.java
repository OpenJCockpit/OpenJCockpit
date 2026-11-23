package nl.metafactory.agents.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import nl.metafactory.common.oauth.ClientCredentialsTokens;

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
                        McpClient.sync(HttpClientSseClientTransport.builder(server.getUrl())
                                        .httpRequestCustomizer(bearerToken(new ClientCredentialsTokens(
                                                new ClientCredentialsTokens.Config(server.getTokenUrl(),
                                                        server.getClientId(), server.getClientSecret()))))
                                        .build())
                                .requestTimeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                                .build()))
                .toList();
    }

    /**
     * Sets this service's client-credentials token on every request (the SSE handshake and each message), fetched
     * per request from the cache so a refreshed token is used. No header when no client is configured.
     */
    static McpSyncHttpClientRequestCustomizer bearerToken(ClientCredentialsTokens tokens) {
        return (request, method, uri, body, context) ->
                tokens.authorization().ifPresent(authorization -> request.setHeader("Authorization", authorization));
    }
}
