package nl.metafactory.agents.gitworkspace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The engine only binds a run to a workspace with a safe key, a repository and a safe branch. */
class GitWorkspaceBindingTest {

    @Test
    void aWorkspaceNeedsASafeKeyARepositoryAndASafeBranch() {
        var ws = new GitWorkspaceBinding("job-1", "https://git.example/r.git", "openjcockpit/wf/abc");
        assertThat(ws.repositoryUrl()).isEqualTo("https://git.example/r.git");

        assertThatThrownBy(() -> new GitWorkspaceBinding(null, "r", "b")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GitWorkspaceBinding("../x", "r", "b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GitWorkspaceBinding("k", null, "b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GitWorkspaceBinding("k", " ", "b")).isInstanceOf(IllegalArgumentException.class);
        for (String branch : new String[]{null, "", "-x", "a..b", "a b", "a\u0007", "x".repeat(256)}) {
            assertThatThrownBy(() -> new GitWorkspaceBinding("k", "r", branch)).as(String.valueOf(branch))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
