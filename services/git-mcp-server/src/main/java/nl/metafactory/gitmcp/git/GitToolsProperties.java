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

    public String getWorkspaceBasePath() { return workspaceBasePath; }
    public void setWorkspaceBasePath(String workspaceBasePath) { this.workspaceBasePath = workspaceBasePath; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public Duration getRunWorkspaceRetention() { return runWorkspaceRetention; }
    public void setRunWorkspaceRetention(Duration runWorkspaceRetention) { this.runWorkspaceRetention = runWorkspaceRetention; }
    public Duration getRunWorkspaceReapInterval() { return runWorkspaceReapInterval; }
    public void setRunWorkspaceReapInterval(Duration runWorkspaceReapInterval) { this.runWorkspaceReapInterval = runWorkspaceReapInterval; }
}
