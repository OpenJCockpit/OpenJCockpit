package nl.metafactory.aicontrol.config;

import nl.metafactory.common.mcp.GitMcpGateway;
import nl.metafactory.common.mcp.LiteLlmMcpToolClient;
import nl.metafactory.common.oauth.ClientCredentialsTokens;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The git MCP gateway (via LiteLLM) and ai-control's service identity for git-workspace jobs. */
@Configuration
public class GitWorkspaceConfig {

    @Bean
    GitMcpGateway gitMcpGateway(GitWorkspaceProperties properties) {
        var gateway = properties.getGateway();
        return new GitMcpGateway(new LiteLlmMcpToolClient(new LiteLlmMcpToolClient.Config(
                gateway.getBaseUrl(), gateway.getApiKey(), gateway.getServerId(), gateway.getTimeout())));
    }

    /** Named so the job path can never confuse it with the user's relayed bearer token. */
    @Bean
    ClientCredentialsTokens jobServiceTokens(GitWorkspaceProperties properties) {
        var identity = properties.getServiceIdentity();
        return new ClientCredentialsTokens(new ClientCredentialsTokens.Config(
                identity.getTokenUrl(), identity.getClientId(), identity.getClientSecret()));
    }
}
