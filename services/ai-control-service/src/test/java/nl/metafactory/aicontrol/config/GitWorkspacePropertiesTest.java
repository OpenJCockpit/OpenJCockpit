package nl.metafactory.aicontrol.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GitWorkspacePropertiesTest {

    @Test
    void bindsTheServiceIdentityAndTheGatewayAccess() {
        var source = new MapConfigurationPropertySource(Map.of(
                "openjcockpit.git-workspace.credential-client-id", "git-mcp",
                "openjcockpit.git-workspace.service-identity.token-url", "http://kc/token",
                "openjcockpit.git-workspace.service-identity.client-id", "ai-control",
                "openjcockpit.git-workspace.service-identity.client-secret", "s3cret",
                "openjcockpit.git-workspace.gateway.base-url", "http://litellm:4000",
                "openjcockpit.git-workspace.gateway.api-key", "sk-git",
                "openjcockpit.git-workspace.gateway.server-id", "git",
                "openjcockpit.git-workspace.gateway.timeout", "30s"));

        var properties = new Binder(source).bind("openjcockpit.git-workspace", GitWorkspaceProperties.class).get();

        assertThat(properties.getCredentialClientId()).isEqualTo("git-mcp");
        assertThat(properties.getServiceIdentity().getTokenUrl()).isEqualTo("http://kc/token");
        assertThat(properties.getServiceIdentity().getClientId()).isEqualTo("ai-control");
        assertThat(properties.getServiceIdentity().getClientSecret()).isEqualTo("s3cret");
        assertThat(properties.getGateway().getBaseUrl()).isEqualTo("http://litellm:4000");
        assertThat(properties.getGateway().getApiKey()).isEqualTo("sk-git");
        assertThat(properties.getGateway().getServerId()).isEqualTo("git");
        assertThat(properties.getGateway().getTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void defaultsToTheLocalGitMcpServer() {
        var properties = new GitWorkspaceProperties();

        assertThat(properties.getCredentialClientId()).isEqualTo("openjcockpit-git-mcp");
        assertThat(properties.getGateway().getServerId()).isEqualTo("openjcockpit_git");
        assertThat(properties.getGateway().getTimeout()).isEqualTo(Duration.ofSeconds(180));
    }
}
