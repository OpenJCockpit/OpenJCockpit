package nl.metafactory.aicontrol.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Git-workspace jobs work in a workspace of the git MCP server, reached through the LiteLLM gateway,
 * and run their workflow in embabel-agent-service with ai-control's own service identity.
 */
@ConfigurationProperties(prefix = "openjcockpit.git-workspace")
public class GitWorkspaceProperties {

    /** Keycloak client (azp) of the git MCP server: the only caller allowed to read job credentials. */
    private String credentialClientId = "openjcockpit-git-mcp";

    /** ai-control's own Keycloak service identity (client credentials) for job calls to embabel. */
    private final ServiceIdentity serviceIdentity = new ServiceIdentity();

    /** LiteLLM gateway access to the git MCP server. */
    private final Gateway gateway = new Gateway();

    public String getCredentialClientId() { return credentialClientId; }
    public void setCredentialClientId(String credentialClientId) { this.credentialClientId = credentialClientId; }
    public ServiceIdentity getServiceIdentity() { return serviceIdentity; }
    public Gateway getGateway() { return gateway; }

    public static class ServiceIdentity {
        private String tokenUrl;
        private String clientId;
        private String clientSecret;

        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    }

    public static class Gateway {
        /** LiteLLM root, e.g. {@code http://litellm:4000}. */
        private String baseUrl;
        /** LiteLLM virtual key granted the git MCP server. */
        private String apiKey;
        /** {@code mcp_servers} entry in the LiteLLM config. */
        private String serverId = "openjcockpit_git";
        private Duration timeout = Duration.ofSeconds(180);

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getServerId() { return serverId; }
        public void setServerId(String serverId) { this.serverId = serverId; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
    }
}
