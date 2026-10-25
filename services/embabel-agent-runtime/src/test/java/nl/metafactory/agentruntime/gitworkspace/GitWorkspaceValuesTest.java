package nl.metafactory.agentruntime.gitworkspace;

import nl.metafactory.agentruntime.workflowrun.WorkflowRunScope;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The runtime validates a run's workspace itself: whatever the agent service reports, unsafe values never reach a tool. */
class GitWorkspaceValuesTest {

    @Test
    void aWorkspaceNeedsASafeKeyARepositoryAndASafeBranch() {
        var ws = new GitWorkspaceBinding("job-1", "https://git.example/r.git", "openjcockpit/wf/abc");
        assertThat(ws.workspaceKey()).isEqualTo("job-1");

        assertThatThrownBy(() -> new GitWorkspaceBinding(null, "r", "b")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GitWorkspaceBinding("../x", "r", "b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GitWorkspaceBinding("k", " ", "b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GitWorkspaceBinding("k", null, "b")).isInstanceOf(IllegalArgumentException.class);
        for (String branch : new String[]{null, "", "-x", "a..b", "a b", "a\u0007", "x".repeat(256)}) {
            assertThatThrownBy(() -> new GitWorkspaceBinding("k", "r", branch)).as(String.valueOf(branch))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new WorkflowRunScope("wf", null, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void bindsTheEngineIdentityAndTheGateway() {
        var source = new MapConfigurationPropertySource(Map.of(
                "openjcockpit.git-workspace.engine-a2a-client-id", "engine",
                "openjcockpit.git-workspace.gateway-base-url", "http://litellm:4000",
                "openjcockpit.git-workspace.gateway-api-key", "sk",
                "openjcockpit.git-workspace.server-id", "git",
                "openjcockpit.git-workspace.timeout", "30s"));

        var properties = new Binder(source).bind("openjcockpit.git-workspace", GitWorkspaceProperties.class).get();

        assertThat(properties.getEngineA2aClientId()).isEqualTo("engine");
        assertThat(properties.getGatewayBaseUrl()).isEqualTo("http://litellm:4000");
        assertThat(properties.getGatewayApiKey()).isEqualTo("sk");
        assertThat(properties.getServerId()).isEqualTo("git");
        assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(30));
    }
}
