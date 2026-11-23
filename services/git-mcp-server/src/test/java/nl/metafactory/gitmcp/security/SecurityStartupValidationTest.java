package nl.metafactory.gitmcp.security;

import nl.metafactory.gitmcp.GitMcpServerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The whole application started with (in)complete security configuration: incomplete must fail closed. */
class SecurityStartupValidationTest {

    private static ConfigurableApplicationContext start(String... properties) {
        // Command-line arguments: they take precedence over application.yml, like the container's environment.
        String[] args = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of("server.port=0", "spring.main.banner-mode=off"),
                        java.util.Arrays.stream(properties))
                .map(property -> "--" + property).toArray(String[]::new);
        return new SpringApplicationBuilder(GitMcpServerApplication.class).run(args);
    }

    @Test
    void enabledWithoutIssuerAudienceAndAzpRefusesToStart() {
        assertThatThrownBy(() -> start("openjcockpit.git-mcp.security.keycloak-enabled=true"))
                .rootCause()
                .hasMessageContaining("KEYCLOAK_ISSUER_URI is not set")
                .hasMessageContaining("KEYCLOAK_EXPECTED_AUDIENCE is not set")
                .hasMessageContaining("KEYCLOAK_ALLOWED_AZP is not set");
    }

    @Test
    void enabledWithAnInvalidIssuerRefusesToStart() {
        assertThatThrownBy(() -> start("openjcockpit.git-mcp.security.keycloak-enabled=true",
                "openjcockpit.git-mcp.security.issuer-uri=keycloak/realms/openjcockpit",
                "openjcockpit.git-mcp.security.expected-audience=openjcockpit-git-mcp",
                "openjcockpit.git-mcp.security.allowed-azp=openjcockpit-litellm-git"))
                .rootCause().hasMessageContaining("KEYCLOAK_ISSUER_URI is not an absolute http(s) URL");
    }

    @Test
    void anUnsupportedSwitchValueRefusesToStartInsteadOfPickingAMode() {
        assertThatThrownBy(() -> start("openjcockpit.git-mcp.security.keycloak-enabled=yes"))
                .rootCause().hasMessageContaining("KEYCLOAK_ENABLED must be 'true' or 'false'");
    }

    @Test
    void prodProfileDefaultsToEnabledAndFailsClosedWithoutKeycloakSettings() {
        assertThatThrownBy(() -> start("spring.profiles.active=prod",
                "GIT_MCP_WORKSPACE_BASE=target/prod-workspaces", "GIT_MCP_AI_CONTROL_URL=http://ai-control:8080",
                "KEYCLOAK_TOKEN_URL=http://keycloak:8080/token", "GIT_MCP_CLIENT_SECRET=s3cr3t-value-for-tests",
                "MANAGEMENT_PORT=0", "logging.structured.format.console="))
                .rootCause().hasMessageContaining("KEYCLOAK_ISSUER_URI is not set");
    }

    @Test
    void enabledWithCompleteSettingsStartsWithoutContactingKeycloak() {
        // Nothing listens on this issuer: keys are fetched on the first token, never at startup.
        try (var context = start("openjcockpit.git-mcp.security.keycloak-enabled=true",
                "openjcockpit.git-mcp.security.issuer-uri=http://127.0.0.1:9/realms/openjcockpit",
                "openjcockpit.git-mcp.security.expected-audience=openjcockpit-git-mcp",
                "openjcockpit.git-mcp.security.allowed-azp=openjcockpit-litellm-git")) {
            assertThat(context.getBeanNamesForType(JwtDecoder.class)).containsExactly("mcpJwtDecoder");
        }
    }
}
