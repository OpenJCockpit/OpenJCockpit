package nl.metafactory.agents.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpClientPropertiesTest {

    @Test
    void defaultsAreDisabledWithoutServers() {
        var properties = new McpClientProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getRequestTimeoutSeconds()).isEqualTo(120);
        assertThat(properties.getServers()).isEmpty();
    }

    @Test
    void settersOverrideDefaults() {
        var properties = new McpClientProperties();
        var server = new McpClientProperties.McpServer();
        server.setName("git");
        server.setUrl("http://git-mcp-server:8093");

        properties.setEnabled(true);
        properties.setRequestTimeoutSeconds(30);
        properties.setServers(List.of(server));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getRequestTimeoutSeconds()).isEqualTo(30);
        assertThat(properties.getServers()).hasSize(1);
        assertThat(properties.getServers().get(0).getName()).isEqualTo("git");
        assertThat(properties.getServers().get(0).getUrl()).isEqualTo("http://git-mcp-server:8093");
    }
}
