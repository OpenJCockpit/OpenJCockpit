package nl.metafactory.agents.gitworkspace;

import com.embabel.agent.api.tool.Tool;
import nl.metafactory.agents.definition.runtime.RunContext;
import nl.metafactory.agents.definition.tool.RunScopedTool;
import nl.metafactory.common.mcp.GitMcpGateway;
import nl.metafactory.common.mcp.LiteLlmMcpToolClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GitWorkspaceToolsTest {

    private static final GitWorkspaceBinding WORKSPACE =
            new GitWorkspaceBinding("job-1", "https://github.com/org/repo.git", "openjcockpit/wf/abc");

    private GitMcpGateway gateway;
    private GitWorkspaceTools config;

    @BeforeEach
    void setUp() {
        gateway = mock(GitMcpGateway.class);
        config = new GitWorkspaceTools();
    }

    private static RunContext run(GitWorkspaceBinding workspace) {
        return new RunContext("run-1", "trace-1", "local", "software-developer", "1.1.0", "hash-1", "alice",
                new nl.metafactory.agents.workflowrun.WorkflowRunScope("wf-1", "wf-run-1", workspace));
    }

    private static String text(Tool.Result result) {
        return result instanceof Tool.Result.Text t ? t.getContent() : ((Tool.Result.Error) result).getMessage();
    }

    @Test
    void toolsAreRegisteredUnderTheirIdsAndLlmNames() {
        assertThat(config.gitListFilesTool(gateway).id()).isEqualTo("git.list_files");
        assertThat(config.gitReadFileTool(gateway).tool().getDefinition().getName()).isEqualTo("git_read_file");
        assertThat(config.gitWriteFileTool(gateway).tool()).isInstanceOf(RunScopedTool.class);
    }

    @Test
    void aRunWithoutAGitWorkspaceGetsAClearErrorAndNoGatewayCall() {
        var tool = (RunScopedTool) config.gitReadFileTool(gateway).tool();

        var result = tool.call("{\"path\":\"README.md\"}", run(null));

        assertThat(result).isInstanceOf(Tool.Result.Error.class);
        assertThat(text(result)).contains("No git workspace is bound");
        verifyNoInteractions(gateway);
    }

    @Test
    void toolsUseTheRunsRepositoryAndWorkspaceNeverTheLlmInput() {
        when(gateway.writeFile(any(), any(), any(), any())).thenReturn(new GitMcpGateway.Result(true, "ok", "b", null));
        var tool = (RunScopedTool) config.gitWriteFileTool(gateway).tool();

        // extra fields an LLM might invent are ignored: the binding decides where the file goes
        var result = tool.call("{\"path\":\"src/App.java\",\"content\":\"class App {}\","
                + "\"workspaceKey\":\"job-other\",\"repositoryUrl\":\"https://evil.example/x.git\"}", run(WORKSPACE));

        assertThat(text(result)).isEqualTo("Wrote src/App.java");
        verify(gateway).writeFile("https://github.com/org/repo.git", "src/App.java", "class App {}", "job-1");
    }

    @Test
    void listAndReadReturnTheWorkspaceContent() {
        when(gateway.listFiles(any(), any(), any())).thenReturn(new GitMcpGateway.FileList(true, "ok", List.of("a.md", "b.md")));
        when(gateway.readFile(any(), any(), any())).thenReturn(new GitMcpGateway.FileContent(true, "ok", "a.md", "# A"));

        assertThat(text(((RunScopedTool) config.gitListFilesTool(gateway).tool()).call("{}", run(WORKSPACE))))
                .isEqualTo("a.md\nb.md");
        assertThat(text(((RunScopedTool) config.gitReadFileTool(gateway).tool()).call("{\"path\":\"a.md\"}", run(WORKSPACE))))
                .isEqualTo("# A");
        verify(gateway).listFiles("https://github.com/org/repo.git", null, "job-1");
    }

    @Test
    void oversizedWritesAndUnreachableGatewaysAreToolErrors() {
        var write = (RunScopedTool) config.gitWriteFileTool(gateway).tool();
        String huge = "x".repeat(GitWorkspaceTools.MAX_WRITE_CHARS + 1);

        assertThat(write.call("{\"path\":\"a\",\"content\":\"" + huge + "\"}", run(WORKSPACE)))
                .isInstanceOf(Tool.Result.Error.class);
        when(gateway.readFile(any(), any(), any())).thenThrow(new LiteLlmMcpToolClient.McpCallException("HTTP 502"));
        assertThat(text(((RunScopedTool) config.gitReadFileTool(gateway).tool()).call("{\"path\":\"a\"}", run(WORKSPACE))))
                .isEqualTo("git workspace unavailable");
        assertThat(write.call("not json", run(WORKSPACE))).isInstanceOf(Tool.Result.Error.class);
    }

    @Test
    void theBindingRejectsUnsafeKeys() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new GitWorkspaceBinding("../x", "https://r", "b"))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new GitWorkspaceBinding("job-1", " ", "b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gatewayFailuresBecomeToolErrors() {
        when(gateway.listFiles(any(), any(), any())).thenReturn(new GitMcpGateway.FileList(false, "no such folder", List.of()));
        when(gateway.readFile(any(), any(), any())).thenReturn(new GitMcpGateway.FileContent(false, "not found", null, null));
        when(gateway.writeFile(any(), any(), any(), any())).thenReturn(new GitMcpGateway.Result(false, "read-only", null, null));

        var listed = ((RunScopedTool) config.gitListFilesTool(gateway).tool()).call("{\"subdirectory\":\"src\"}", run(WORKSPACE));
        var read = ((RunScopedTool) config.gitReadFileTool(gateway).tool()).call("{\"path\":\"a.md\"}", run(WORKSPACE));
        var written = ((RunScopedTool) config.gitWriteFileTool(gateway).tool()).call("{\"path\":\"a.md\",\"content\":\"x\"}", run(WORKSPACE));

        assertThat(text(listed)).isEqualTo("no such folder");
        assertThat(text(read)).isEqualTo("not found");
        assertThat(text(written)).isEqualTo("read-only");
    }

    @Test
    void theToolShapeRefusesOutsideARunAndKeepsItsMetadata() {
        var tool = config.gitReadFileTool(gateway).tool();

        assertThat(tool.getMetadata()).isNotNull();
        assertThat(text(tool.call("{}"))).contains("only available inside an agent run");
        verifyNoInteractions(gateway);
    }
}
