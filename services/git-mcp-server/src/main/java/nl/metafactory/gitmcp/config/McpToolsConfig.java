package nl.metafactory.gitmcp.config;

import nl.metafactory.gitmcp.git.GitHubToolsService;
import nl.metafactory.gitmcp.git.GitToolsService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the git and GitHub tools with the Spring AI MCP server. */
@Configuration
public class McpToolsConfig {

    @Bean
    public ToolCallbackProvider gitToolCallbacks(GitToolsService gitToolsService,
                                                 GitHubToolsService gitHubToolsService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(gitToolsService, gitHubToolsService).build();
    }
}
