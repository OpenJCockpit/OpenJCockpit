package nl.metafactory.gitmcp.config;

import nl.metafactory.gitmcp.git.GitToolsProperties;
import nl.metafactory.gitmcp.git.GitToolsService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class McpToolsConfigTest {

    @Test
    void registersAllNineGitToolsWithTheMcpServer() {
        nl.metafactory.gitmcp.git.GitCredentialResolver anonymous = (key, url) -> java.util.Optional.empty();
        var service = new GitToolsService(mock(nl.metafactory.gitmcp.git.GitWorkspaceOperations.class),
                new GitToolsProperties(), anonymous);
        var gitHubService = new nl.metafactory.gitmcp.git.GitHubToolsService(
                mock(nl.metafactory.gitmcp.git.GitHubOperations.class), anonymous);

        var provider = new McpToolsConfig().gitToolCallbacks(service, gitHubService);
        var toolNames = Arrays.stream(provider.getToolCallbacks())
                .map(ToolCallback::getToolDefinition)
                .map(definition -> definition.name())
                .toList();

        assertThat(toolNames).containsExactlyInAnyOrder(
                "git_checkout_branch", "git_create_branch", "git_pull", "git_commit",
                "git_list_files", "git_read_file", "git_write_file", "git_push",
                "git_create_pull_request");
    }
}
