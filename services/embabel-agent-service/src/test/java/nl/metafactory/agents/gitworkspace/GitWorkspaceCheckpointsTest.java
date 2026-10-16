package nl.metafactory.agents.gitworkspace;

import nl.metafactory.agents.workflowrun.WorkflowRunScope;
import nl.metafactory.common.mcp.GitMcpGateway;
import nl.metafactory.common.mcp.LiteLlmMcpToolClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GitWorkspaceCheckpointsTest {

    private static final String REPO = "https://github.com/org/repo.git";
    private static final GitWorkspaceBinding WS = new GitWorkspaceBinding("job-1", REPO, "openjcockpit/wf-1/abc");
    private static final WorkflowRunScope SCOPE = new WorkflowRunScope("wf-1", "run-1", WS);

    private final GitMcpGateway git = mock(GitMcpGateway.class);
    private final GitWorkspaceProperties properties = new GitWorkspaceProperties();
    private final GitWorkspaceCheckpoints checkpoints = new GitWorkspaceCheckpoints(git, properties);

    private static GitMcpGateway.Result ok(String hash) {
        return new GitMcpGateway.Result(true, "ok", "openjcockpit/wf-1/abc", hash);
    }

    @Test
    void writesTheOutputCommitsAndPushesTheRunsBranch() {
        when(git.writeFile(anyString(), anyString(), anyString(), anyString())).thenReturn(ok(null));
        when(git.commit(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(ok("abcdef123456"));
        when(git.push(anyString(), anyString(), anyString())).thenReturn(ok(null));

        var outcome = checkpoints.afterAgent(SCOPE, "a2a:litellm:software-developer", 2, 4, "the code");

        assertThat(outcome).contains("Checkpoint abcdef1 pushed to openjcockpit/wf-1/abc");
        verify(git).writeFile(eq(REPO), eq("agent-output/run-1/03-software-developer.md"), contains("the code"), eq("job-1"));
        verify(git).commit(eq(REPO), contains("checkpoint 3/4: software-developer"), anyString(), anyString(), eq("job-1"));
        verify(git).push(REPO, "openjcockpit/wf-1/abc", "job-1");
    }

    @Test
    void nothingToCommitIsNotPushed() {
        when(git.writeFile(anyString(), anyString(), anyString(), anyString())).thenReturn(ok(null));
        when(git.commit(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new GitMcpGateway.Result(false, "Nothing to commit: clean", null, null));

        assertThat(checkpoints.afterAgent(SCOPE, "business-analyst", 0, 2, "x")).get().asString().contains("nothing to commit");
        verify(git, never()).push(any(), any(), any());
    }

    @Test
    void aFailingGatewayIsReportedNotThrown() {
        when(git.writeFile(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new LiteLlmMcpToolClient.McpCallException("down"));

        assertThat(checkpoints.afterAgent(SCOPE, "business-analyst", 0, 2, "x")).get().asString()
                .contains("failed: git workspace unavailable");
    }

    @Test
    void aFailedPushIsReported() {
        when(git.writeFile(anyString(), anyString(), anyString(), anyString())).thenReturn(ok(null));
        when(git.commit(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(ok("abcdef123456"));
        when(git.push(anyString(), anyString(), anyString())).thenReturn(new GitMcpGateway.Result(false, "rejected", null, null));

        assertThat(checkpoints.afterAgent(SCOPE, "qa", 0, 1, "x")).get().asString()
                .contains("committed abcdef1 but push failed: rejected");
    }

    @Test
    void runsWithoutWorkspaceOrWithCheckpointsOffAreSkipped() {
        assertThat(checkpoints.afterAgent(new WorkflowRunScope("wf-1", "run-1", null), "qa", 0, 1, "x")).isEmpty();
        assertThat(checkpoints.afterAgent(null, "qa", 0, 1, "x")).isEmpty();
        properties.setCheckpoints(false);
        assertThat(checkpoints.afterAgent(SCOPE, "qa", 0, 1, "x")).isEmpty();
        verifyNoInteractions(git);
    }
}
