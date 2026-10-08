package nl.metafactory.agents.mcp;

import nl.metafactory.agents.policy.mcp.McpToolExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Activates the central MCP client when metafactory.mcp.enabled=true; otherwise
 * the NoOpMcpToolExecutor stays active and the behaviour does not change.
 */
@Configuration
public class McpClientConfig {

    @Bean
    @ConditionalOnProperty(prefix = "metafactory.mcp", name = "enabled", havingValue = "true")
    public McpToolExecutor remoteMcpToolExecutor(McpClientProperties properties) {
        return new RemoteMcpToolExecutor(new McpConnectionFactory(properties).connections());
    }
}
