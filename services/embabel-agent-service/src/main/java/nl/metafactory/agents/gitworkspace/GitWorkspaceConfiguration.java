package nl.metafactory.agents.gitworkspace;

import nl.metafactory.common.mcp.GitMcpGateway;
import nl.metafactory.common.mcp.LiteLlmMcpToolClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The git MCP server (through LiteLLM) used for the checkpoint commits of runs bound to a git workspace. */
@Configuration
public class GitWorkspaceConfiguration {

    @Bean
    GitMcpGateway agentGitMcpGateway(GitWorkspaceProperties properties) {
        return new GitMcpGateway(new LiteLlmMcpToolClient(new LiteLlmMcpToolClient.Config(
                properties.getGatewayBaseUrl(), properties.getGatewayApiKey(), properties.getServerId(),
                properties.getTimeout())));
    }
}
