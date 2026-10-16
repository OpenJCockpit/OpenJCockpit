package nl.metafactory.agents.gitworkspace;

import com.embabel.agent.api.tool.Tool;
import nl.metafactory.agents.definition.runtime.RunContext;
import nl.metafactory.agents.definition.tool.RegisteredAgentTool;
import nl.metafactory.agents.definition.tool.RunScopedTool;
import nl.metafactory.common.mcp.GitMcpGateway;
import nl.metafactory.common.mcp.LiteLlmMcpToolClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.util.function.BiFunction;

/**
 * Git tools for configuration-driven agents ({@code git.list_files}, {@code git.read_file},
 * {@code git.write_file}). They work only in the git workspace the workflow run is bound to (a
 * clone in the git MCP server that ai-control prepared), reached through the LiteLLM gateway. The repository
 * URL and workspace key come from the run, never from the LLM; credentials never pass through here.
 * Committing and pushing are left to the job. Like every tool, an agent only gets them when its
 * definition requests them and its tenant binding allows them.
 */
@Configuration
public class GitWorkspaceTools {

    private static final Logger log = LoggerFactory.getLogger(GitWorkspaceTools.class);
    static final int MAX_WRITE_CHARS = 200_000;
    static final String UNBOUND = "No git workspace is bound to this run: git tools are only available "
            + "when the workflow runs on a project (ai-control prepares the workspace).";

    public record ListFilesInput(String subdirectory) {
    }

    public record ReadFileInput(String path) {
    }

    public record WriteFileInput(String path, String content) {
    }

    @Bean
    GitMcpGateway agentGitMcpGateway(GitWorkspaceProperties properties) {
        return new GitMcpGateway(new LiteLlmMcpToolClient(new LiteLlmMcpToolClient.Config(
                properties.getGatewayBaseUrl(), properties.getGatewayApiKey(), properties.getServerId(),
                properties.getTimeout())));
    }

    @Bean
    RegisteredAgentTool gitListFilesTool(GitMcpGateway agentGitMcpGateway) {
        return new RegisteredAgentTool("git.list_files", new WorkspaceTool<>("git_list_files",
                "List file paths (relative to the repository root) of the project repository this run works on, "
                        + "optionally limited to a subdirectory.",
                ListFilesInput.class, (input, ws) -> {
                    var listed = agentGitMcpGateway.listFiles(ws.repositoryUrl(), blankToNull(input.subdirectory()),
                            ws.workspaceKey());
                    return listed.success() ? Tool.Result.text(String.join("\n", listed.files()))
                            : Tool.Result.error(listed.message());
                }));
    }

    @Bean
    RegisteredAgentTool gitReadFileTool(GitMcpGateway agentGitMcpGateway) {
        return new RegisteredAgentTool("git.read_file", new WorkspaceTool<>("git_read_file",
                "Read the text content of a file (path relative to the repository root) of the project repository "
                        + "this run works on.",
                ReadFileInput.class, (input, ws) -> {
                    var file = agentGitMcpGateway.readFile(ws.repositoryUrl(), input.path(), ws.workspaceKey());
                    return file.success() ? Tool.Result.text(file.content() == null ? "" : file.content())
                            : Tool.Result.error(file.message());
                }));
    }

    @Bean
    RegisteredAgentTool gitWriteFileTool(GitMcpGateway agentGitMcpGateway) {
        return new RegisteredAgentTool("git.write_file", new WorkspaceTool<>("git_write_file",
                "Write the full text content of a file (path relative to the repository root) in the project "
                        + "repository this run works on. Creates parent directories. Your changes are committed and "
                        + "pushed automatically after you finish; do not try to commit yourself.",
                WriteFileInput.class, (input, ws) -> {
                    if (input.content() != null && input.content().length() > MAX_WRITE_CHARS) {
                        return Tool.Result.error("content too large (max " + MAX_WRITE_CHARS + " characters)");
                    }
                    var written = agentGitMcpGateway.writeFile(ws.repositoryUrl(), input.path(),
                            input.content() == null ? "" : input.content(), ws.workspaceKey());
                    return written.success() ? Tool.Result.text("Wrote " + input.path())
                            : Tool.Result.error(written.message());
                }));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * A git tool bound to the calling run's workspace. Input and output are never logged (they contain
     * repository content); only the tool, the run and the outcome are.
     */
    static final class WorkspaceTool<I> implements RunScopedTool {

        private final Tool shape;
        private final Class<I> inputType;
        private final BiFunction<I, GitWorkspaceBinding, Tool.Result> body;
        // fields the LLM invents (e.g. a workspace or repository) are ignored, never used
        private final JsonMapper json = JsonMapper.builder()
                .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

        WorkspaceTool(String name, String description, Class<I> inputType,
                      BiFunction<I, GitWorkspaceBinding, Tool.Result> body) {
            this.shape = Tool.create(name, description, Tool.InputSchema.of(inputType),
                    input -> Tool.Result.error("this tool is only available inside an agent run"));
            this.inputType = inputType;
            this.body = body;
        }

        @Override
        public Definition getDefinition() {
            return shape.getDefinition();
        }

        @Override
        public Metadata getMetadata() {
            return shape.getMetadata();
        }

        @Override
        public Tool.Result call(String input, RunContext run) {
            GitWorkspaceBinding workspace = run == null ? null : run.gitWorkspace();
            if (workspace == null) {
                return Tool.Result.error(UNBOUND);
            }
            I parsed;
            try {
                parsed = json.readValue(input == null || input.isBlank() ? "{}" : input, inputType);
            } catch (RuntimeException e) {
                return Tool.Result.error("invalid tool input");
            }
            try {
                var result = body.apply(parsed, workspace);
                log.info("agent.git.tool tool={} workspace={} runId={} ok={}", shape.getDefinition().getName(),
                        workspace.workspaceKey(), run.runId(), !(result instanceof Tool.Result.Error));
                return result;
            } catch (LiteLlmMcpToolClient.McpCallException e) {
                log.warn("agent.git.tool.failed tool={} workspace={} runId={} reason={}",
                        shape.getDefinition().getName(), workspace.workspaceKey(), run.runId(), e.getMessage());
                return Tool.Result.error("git workspace unavailable");
            }
        }
    }
}
