package nl.metafactory.agents.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpConnectionFactoryTest {

    private McpClientProperties.McpServer server(String name, String url) {
        var server = new McpClientProperties.McpServer();
        server.setName(name);
        server.setUrl(url);
        return server;
    }

    @Test
    void buildsOneConnectionPerConfiguredServerWithoutConnecting() {
        var properties = new McpClientProperties();
        properties.setServers(List.of(
                server("git", "http://localhost:8093"),
                server("jira", "http://localhost:8094")));

        var connections = new McpConnectionFactory(properties).connections();

        assertThat(connections).hasSize(2);
        assertThat(connections.get(0).name()).isEqualTo("git");
        assertThat(connections.get(1).name()).isEqualTo("jira");
    }

    @Test
    void buildsNoConnectionsWithoutServers() {
        assertThat(new McpConnectionFactory(new McpClientProperties()).connections()).isEmpty();
    }
}
