package nl.metafactory.gitmcp.git;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GitToolsPropertiesTest {

    @Test
    void defaultsPointToTempWorkspaceWithSaneTimeout() {
        var properties = new GitToolsProperties();

        assertThat(properties.getWorkspaceBasePath()).contains("git-mcp-workspaces");
        assertThat(properties.getTimeoutSeconds()).isEqualTo(120);
        assertThat(properties.getRunWorkspaceRetention()).isEqualTo(Duration.ofHours(24));
        assertThat(properties.getRunWorkspaceReapInterval()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void settersOverrideDefaults() {
        var properties = new GitToolsProperties();
        properties.setWorkspaceBasePath("/data/workspaces");
        properties.setTimeoutSeconds(30);
        properties.setRunWorkspaceRetention(Duration.ofMinutes(5));
        properties.setRunWorkspaceReapInterval(Duration.ofSeconds(30));

        assertThat(properties.getWorkspaceBasePath()).isEqualTo("/data/workspaces");
        assertThat(properties.getTimeoutSeconds()).isEqualTo(30);
        assertThat(properties.getRunWorkspaceRetention()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.getRunWorkspaceReapInterval()).isEqualTo(Duration.ofSeconds(30));
    }
}
