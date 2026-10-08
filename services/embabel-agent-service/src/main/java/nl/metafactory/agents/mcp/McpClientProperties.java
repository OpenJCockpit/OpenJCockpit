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

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
}
