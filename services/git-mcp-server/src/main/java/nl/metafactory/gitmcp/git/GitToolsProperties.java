package nl.metafactory.gitmcp.git;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("openjcockpit.git-mcp")
public class GitToolsProperties {

    /** Base directory that holds one workspace clone per repository. */
    private String workspaceBasePath = System.getProperty("java.io.tmpdir") + "/git-mcp-workspaces";

    /** Timeout for remote git operations (clone, pull, push). */
    private int timeoutSeconds = 120;

    /** How long a run workspace is retained before it is eligible for reaping. */
    private Duration runWorkspaceRetention = Duration.ofHours(24);

    /** How often the reaper checks for expired run workspaces. */
    private Duration runWorkspaceReapInterval = Duration.ofHours(1);

    /** Server-side credentials of keyed workspaces (pulled from ai-control-service). */
    private final Credentials credentials = new Credentials();

    public Credentials getCredentials() { return credentials; }

    /**
     * Where keyed workspaces get their git credentials: ai-control-service, called with this server's
     * Keycloak service identity (client credentials). All blank → keyed workspaces fail closed.
     */
    public static class Credentials {
        private String aiControlUrl;
        private String tokenUrl;
        private String clientId;
        private String clientSecret;

        public boolean configured() {
            return notBlank(aiControlUrl) && notBlank(tokenUrl) && notBlank(clientId) && notBlank(clientSecret);
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }

        public String getAiControlUrl() { return aiControlUrl; }
        public void setAiControlUrl(String aiControlUrl) { this.aiControlUrl = aiControlUrl; }
        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    }

    public String getWorkspaceBasePath() { return workspaceBasePath; }
    public void setWorkspaceBasePath(String workspaceBasePath) { this.workspaceBasePath = workspaceBasePath; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public Duration getRunWorkspaceRetention() { return runWorkspaceRetention; }
    public void setRunWorkspaceRetention(Duration runWorkspaceRetention) { this.runWorkspaceRetention = runWorkspaceRetention; }
    public Duration getRunWorkspaceReapInterval() { return runWorkspaceReapInterval; }
    public void setRunWorkspaceReapInterval(Duration runWorkspaceReapInterval) { this.runWorkspaceReapInterval = runWorkspaceReapInterval; }
}
