package nl.metafactory.agents.gitworkspace;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Git-workspace binding of workflow runs (tickets) and the checkpoint commits after each agent (git MCP
 * server via LiteLLM). The agents' own git tools run in embabel-agent-runtime with its own key.
 */
@Component
@ConfigurationProperties("openjcockpit.git-workspace")
public class GitWorkspaceProperties {

    /**
     * Keycloak client (azp) of ai-control-service: the only caller that may issue git-workspace tickets,
     * which bind a workflow start to a git workspace.
     */
    private String trustedClientId = "openjcockpit-ai-control";

    /** LiteLLM root (e.g. {@code http://litellm:4000}); blank disables checkpoint commits. */
    private String gatewayBaseUrl;

    /** LiteLLM virtual key granted the git MCP server ({@code object_permission.mcp_servers}). */
    private String gatewayApiKey;

    /** {@code mcp_servers} entry of the git MCP server in the LiteLLM config. */
    private String serverId = "openjcockpit_git";

    private Duration timeout = Duration.ofSeconds(120);

    /** How long an issued git-workspace ticket can be redeemed by its workflow start. */
    private Duration ticketTtl = Duration.ofMinutes(2);

    /** Commit and push the run's branch after every agent (checkpoint), so finished agents' work survives. */
    private boolean checkpoints = true;

    public Duration getTicketTtl() { return ticketTtl; }
    public void setTicketTtl(Duration ticketTtl) { this.ticketTtl = ticketTtl; }
    public boolean isCheckpoints() { return checkpoints; }
    public void setCheckpoints(boolean checkpoints) { this.checkpoints = checkpoints; }

    public String getTrustedClientId() { return trustedClientId; }
    public void setTrustedClientId(String trustedClientId) { this.trustedClientId = trustedClientId; }
    public String getGatewayBaseUrl() { return gatewayBaseUrl; }
    public void setGatewayBaseUrl(String gatewayBaseUrl) { this.gatewayBaseUrl = gatewayBaseUrl; }
    public String getGatewayApiKey() { return gatewayApiKey; }
    public void setGatewayApiKey(String gatewayApiKey) { this.gatewayApiKey = gatewayApiKey; }
    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }
}
