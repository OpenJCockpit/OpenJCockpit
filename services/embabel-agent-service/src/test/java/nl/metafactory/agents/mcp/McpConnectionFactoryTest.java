package nl.metafactory.agents.mcp;

import nl.metafactory.common.oauth.ClientCredentialsTokens;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Optional;

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

    private static HttpRequest customized(ClientCredentialsTokens tokens) {
        var request = HttpRequest.newBuilder(URI.create("http://git-mcp-server:8093/sse"))
                .header("Authorization", "Bearer stale");
        McpConnectionFactory.bearerToken(tokens).customize(request, "GET", URI.create("http://git-mcp-server:8093/sse"),
                null, null);
        return request.build();
    }

    @Test
    void sendsTheServiceTokenOnEveryRequestWhenAClientIsConfigured() {
        var tokens = new ClientCredentialsTokens(new ClientCredentialsTokens.Config("http://kc/token", "c", "s")) {
            @Override
            public synchronized Optional<String> authorization() {
                return Optional.of("Bearer fresh");
            }
        };

        assertThat(customized(tokens).headers().allValues("Authorization")).containsExactly("Bearer fresh");
    }

    @Test
    void sendsNoTokenWithoutAConfiguredClient() {
        var tokens = new ClientCredentialsTokens(new ClientCredentialsTokens.Config(null, null, null));

        assertThat(customized(tokens).headers().allValues("Authorization")).containsExactly("Bearer stale");
    }

    @Test
    void buildsAuthenticatedConnectionsWithoutFetchingATokenYet() {
        var properties = new McpClientProperties();
        var git = server("git", "http://localhost:8093");
        git.setTokenUrl("http://127.0.0.1:9/realms/openjcockpit/protocol/openid-connect/token");
        git.setClientId("openjcockpit-agent-service");
        git.setClientSecret("secret");
        properties.setServers(List.of(git));

        assertThat(new McpConnectionFactory(properties).connections()).hasSize(1);
    }
}
