package nl.metafactory.common.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitMcpGatewayTest {

    private static final String REPO = "https://github.com/org/repo.git";

    private MockRestServiceServer server;
    private GitMcpGateway gateway;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var client = new LiteLlmMcpToolClient(new LiteLlmMcpToolClient.Config("http://litellm:4000/", "sk-ai-control",
                "openjcockpit_git", Duration.ofSeconds(5)), builder.build());
        gateway = new GitMcpGateway(client);
    }

    private static String toolResult(String json, boolean error) {
        return "{\"content\":[{\"type\":\"text\",\"text\":" + quote(json) + "}],\"isError\":" + error + "}";
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Test
    void callsTheToolThroughLiteLlmsRestBridgeWithoutAnyCredentialArgument() {
        server.expect(requestTo("http://litellm:4000/mcp-rest/tools/call")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-ai-control"))
                .andExpect(content().json("{\"server_id\":\"openjcockpit_git\",\"name\":\"git_create_branch\","
                        + "\"arguments\":{\"repositoryUrl\":\"" + REPO + "\",\"baseBranch\":\"main\","
                        + "\"newBranch\":\"agentic/x\",\"workspaceKey\":\"job-1\"}}", true))
                .andRespond(withSuccess(toolResult("{\"success\":true,\"message\":\"Created\",\"branch\":\"agentic/x\"}", false),
                        MediaType.APPLICATION_JSON));

        var result = gateway.createBranch(REPO, "main", "agentic/x", "job-1");

        assertThat(result.success()).isTrue();
        assertThat(result.branch()).isEqualTo("agentic/x");
        server.verify();
    }

    @Test
    void parsesCommitFileAndListResults() {
        server.expect(requestTo("http://litellm:4000/mcp-rest/tools/call"))
                .andRespond(withSuccess(toolResult("{\"success\":true,\"message\":\"Committed\",\"commitHash\":\"abc\"}", false),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://litellm:4000/mcp-rest/tools/call"))
                .andRespond(withSuccess(toolResult("{\"success\":true,\"message\":\"Read\",\"path\":\"a.md\",\"content\":\"# A\"}", false),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://litellm:4000/mcp-rest/tools/call"))
                .andRespond(withSuccess(toolResult("{\"success\":true,\"message\":\"Listed\",\"files\":[\"a.md\"]}", false),
                        MediaType.APPLICATION_JSON));

        assertThat(gateway.commit(REPO, "msg", null, null, "job-1").commitHash()).isEqualTo("abc");
        assertThat(gateway.readFile(REPO, "a.md", "job-1").content()).isEqualTo("# A");
        assertThat(gateway.listFiles(REPO, null, "job-1").files()).containsExactly("a.md");
    }

    @Test
    void gatewayErrorsAndMcpErrorsBecomeMcpCallExceptionsWithoutContent() {
        server.expect(requestTo("http://litellm:4000/mcp-rest/tools/call")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo("http://litellm:4000/mcp-rest/tools/call"))
                .andRespond(withSuccess(toolResult("secret repository content", true), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.push(REPO, "b", "job-1"))
                .isInstanceOf(LiteLlmMcpToolClient.McpCallException.class).hasMessageContaining("HTTP 403");
        assertThatThrownBy(() -> gateway.push(REPO, "b", "job-1"))
                .isInstanceOf(LiteLlmMcpToolClient.McpCallException.class)
                .hasMessageNotContaining("secret repository content");
    }

    @Test
    void anUnconfiguredGatewayRefusesWithoutCalling() {
        var unconfigured = new GitMcpGateway(new LiteLlmMcpToolClient(
                new LiteLlmMcpToolClient.Config(null, null, "openjcockpit_git", null)));

        assertThat(unconfigured.configured()).isFalse();
        assertThatThrownBy(() -> unconfigured.push(REPO, "b", "job-1"))
                .isInstanceOf(LiteLlmMcpToolClient.McpCallException.class).hasMessageContaining("not configured");
        assertThat(new LiteLlmMcpToolClient.Config("u", "sk-secret", "s", null).toString()).doesNotContain("sk-secret");
    }
}
