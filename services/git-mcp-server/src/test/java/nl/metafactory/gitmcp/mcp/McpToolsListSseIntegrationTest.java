package nl.metafactory.gitmcp.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpToolsListSseIntegrationTest {

    @LocalServerPort
    private int port;

    private McpSyncClient client;

    @BeforeEach
    void connect() {
        var transport = HttpClientSseClientTransport.builder("http://localhost:" + port).build();
        client = McpClient.sync(transport).build();
        client.initialize();
    }

    @AfterEach
    void disconnect() {
        if (client != null) {
            client.closeGracefully();
        }
    }

    @Test
    void toolsListMatchesFrozenNineToolBaseline() {
        McpSchema.ListToolsResult result = client.listTools();
        List<McpSchema.Tool> tools = result.tools();

        assertThat(tools).extracting(McpSchema.Tool::name)
            .containsExactlyInAnyOrder(
                "git_checkout_branch", "git_create_branch", "git_pull", "git_commit",
                "git_list_files", "git_read_file", "git_write_file", "git_push",
                "git_create_pull_request");

        Map<String, McpSchema.Tool> byName = tools.stream()
            .collect(Collectors.toMap(McpSchema.Tool::name, t -> t));

        assertTool(byName.get("git_commit"),
            "Stage all changes in the repository workspace and commit them with the given message. Returns the commit hash.",
            List.of("repositoryUrl", "message"),
            List.of("repositoryUrl", "message", "authorName", "authorEmail", "workspaceKey"));

        assertTool(byName.get("git_create_pull_request"),
            "Create a pull request on GitHub for a branch that was pushed to the project repository. Returns the URL of the created pull request.",
            List.of("repositoryUrl", "baseBranch", "headBranch", "title", "token"),
            List.of("repositoryUrl", "baseBranch", "headBranch", "title", "body", "token"));

        assertTool(byName.get("git_list_files"),
            "List the file paths (relative to the repository root) in the repository workspace, optionally limited to a subdirectory. Check out or create a branch first.",
            List.of("repositoryUrl"),
            List.of("repositoryUrl", "subdirectory", "workspaceKey"));

        assertTool(byName.get("git_read_file"),
            "Read the text content of a file at a path relative to the repository root. Check out or create a branch first.",
            List.of("repositoryUrl", "path"),
            List.of("repositoryUrl", "path", "workspaceKey"));

        assertTool(byName.get("git_pull"),
            "Pull the latest changes for the currently checked out branch of the project repository.",
            List.of("repositoryUrl", "branch"),
            List.of("repositoryUrl", "branch", "username", "token", "workspaceKey"));

        assertTool(byName.get("git_create_branch"),
            "Create and check out a new branch from a base branch of the project repository, e.g. for a new spec or implementation change.",
            List.of("repositoryUrl", "baseBranch", "newBranch"),
            List.of("repositoryUrl", "baseBranch", "newBranch", "username", "token", "workspaceKey"));

        assertTool(byName.get("git_checkout_branch"),
            "Check out an existing branch of the project repository. Clones the repository into the server-managed workspace when needed.",
            List.of("repositoryUrl", "branch"),
            List.of("repositoryUrl", "branch", "username", "token", "workspaceKey"));

        assertTool(byName.get("git_push"),
            "Push a branch of the repository workspace to origin so a pull request can be opened.",
            List.of("repositoryUrl", "branch"),
            List.of("repositoryUrl", "branch", "username", "token", "workspaceKey"));

        assertTool(byName.get("git_write_file"),
            "Write text content to a file at a path relative to the repository root, e.g. specs/spec-001.md. Creates parent directories as needed. Check out or create a branch first; commit and push afterwards.",
            List.of("repositoryUrl", "path", "content"),
            List.of("repositoryUrl", "path", "content", "workspaceKey"));
    }

    @SuppressWarnings("unchecked")
    private void assertTool(McpSchema.Tool tool, String expectedDescription,
                             List<String> expectedRequired, List<String> expectedProperties) {
        assertThat(tool).as("tool must exist").isNotNull();
        assertThat(tool.description()).isEqualTo(expectedDescription);
        List<String> required = (List<String>) tool.inputSchema().get("required");
        assertThat(required).containsExactlyElementsOf(expectedRequired);
        Map<String, Object> properties = (Map<String, Object>) tool.inputSchema().get("properties");
        assertThat(properties.keySet()).containsExactlyInAnyOrderElementsOf(expectedProperties);
    }
}
