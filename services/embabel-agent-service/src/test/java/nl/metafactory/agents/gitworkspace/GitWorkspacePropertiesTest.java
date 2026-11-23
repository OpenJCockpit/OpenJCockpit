package nl.metafactory.agents.gitworkspace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GitWorkspacePropertiesTest {

    @Test
    void bindsTheTrustedClientsTicketLifetimeAndGateway() {
        var source = new MapConfigurationPropertySource(Map.of(
                "openjcockpit.git-workspace.trusted-client-id", "ai-control",
                "openjcockpit.git-workspace.gateway-base-url", "http://litellm:4000",
                "openjcockpit.git-workspace.gateway-api-key", "sk-mcp",
                "openjcockpit.git-workspace.gateway-token-url", "http://keycloak:8080/token",
                "openjcockpit.git-workspace.gateway-client-id", "openjcockpit-agent-service",
                "openjcockpit.git-workspace.gateway-client-secret", "s3cret",
                "openjcockpit.git-workspace.checkpoints", "false",
                "openjcockpit.git-workspace.ticket-ttl", "2m",
                "openjcockpit.git-workspace.server-id", "git",
                "openjcockpit.git-workspace.timeout", "30s"));

        var properties = new Binder(source).bind("openjcockpit.git-workspace", GitWorkspaceProperties.class).get();

        assertThat(properties.getTrustedClientId()).isEqualTo("ai-control");
        assertThat(properties.getGatewayBaseUrl()).isEqualTo("http://litellm:4000");
        assertThat(properties.getGatewayApiKey()).isEqualTo("sk-mcp");
        assertThat(properties.getGatewayTokenUrl()).isEqualTo("http://keycloak:8080/token");
        assertThat(properties.getGatewayClientId()).isEqualTo("openjcockpit-agent-service");
        assertThat(properties.getGatewayClientSecret()).isEqualTo("s3cret");
        assertThat(properties.isCheckpoints()).isFalse();
        assertThat(properties.getTicketTtl()).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.getServerId()).isEqualTo("git");
        assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(30));
    }
}
