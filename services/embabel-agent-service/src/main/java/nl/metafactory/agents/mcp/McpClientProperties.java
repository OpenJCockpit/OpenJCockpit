package nl.metafactory.agents.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration of the central MCP client: one or more MCP servers whose
 * tools are available to all agents via the PolicyGuardedMcpToolGateway.
 */
@Component
@ConfigurationProperties("openjcockpit.mcp")
public class McpClientProperties {

    /** Enable the remote MCP tool executor; without it the no-op executor is used. */
    private boolean enabled = false;

    /** Timeout for individual MCP requests (initialize, tools/list, tools/call). */
    private int requestTimeoutSeconds = 120;

    /** MCP servers to connect to; tool calls are routed to the first server that exposes the tool. */
    private List<McpServer> servers = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) { this.requestTimeoutSeconds = requestTimeoutSeconds; }
    public List<McpServer> getServers() { return servers; }
    public void setServers(List<McpServer> servers) { this.servers = servers; }

    public static class McpServer {
        private String name;
        private String url;
        /**
         * Keycloak client whose client-credentials token is sent as {@code Authorization: Bearer} on every
         * request to this server (the SSE handshake and each message). Required when the server authenticates
         * its callers (git-mcp-server with KEYCLOAK_ENABLED=true); all blank -> no Authorization header.
         */
        private String tokenUrl;
        private String clientId;
        private String clientSecret;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    }
}
