package nl.metafactory.agentruntime.gitworkspace;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The agents' git tools (git MCP server via LiteLLM) and the identity whose inbound A2A calls may carry a
 * workflow run. Binding a run to a workspace (tickets) and checkpoint commits stay in the agent service.
 */
@Component
@ConfigurationProperties("openjcockpit.git-workspace")
public class GitWorkspaceProperties {

    /**
     * Keycloak client (azp) the workflow engine calls A2A agents with (a2a-providers[].caller-auth of the
     * agent service). An inbound A2A call carries a run's scope only when it comes from this identity.
     */
    private String engineA2aClientId = "openjcockpit-a2a";

    /** LiteLLM root (e.g. {@code http://litellm:4000}); blank disables the agents' git tools. */
    private String gatewayBaseUrl;

    /** LiteLLM virtual key granted the git MCP server ({@code object_permission.mcp_servers}). */
    private String gatewayApiKey;

    /** {@code mcp_servers} entry of the git MCP server in the LiteLLM config. */
    private String serverId = "openjcockpit_git";

    private Duration timeout = Duration.ofSeconds(120);

    public String getEngineA2aClientId() { return engineA2aClientId; }
    public void setEngineA2aClientId(String engineA2aClientId) { this.engineA2aClientId = engineA2aClientId; }
    public String getGatewayBaseUrl() { return gatewayBaseUrl; }
    public void setGatewayBaseUrl(String gatewayBaseUrl) { this.gatewayBaseUrl = gatewayBaseUrl; }
    public String getGatewayApiKey() { return gatewayApiKey; }
    public void setGatewayApiKey(String gatewayApiKey) { this.gatewayApiKey = gatewayApiKey; }
    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }
}
