package nl.metafactory.agents.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpClientConfigTest {

    @Test
    void createsRemoteExecutorFromConfiguredServers() {
        var properties = new McpClientProperties();
        var server = new McpClientProperties.McpServer();
        server.setName("git");
        server.setUrl("http://localhost:8093");
        properties.setEnabled(true);
        properties.setServers(List.of(server));

        var executor = new McpClientConfig().remoteMcpToolExecutor(properties);

        assertThat(executor).isInstanceOf(RemoteMcpToolExecutor.class);
    }
}
