package nl.metafactory.aicontrol.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakSettingsTest {

    private static final String RESOURCES = "src/main/resources/";

    private static StandardEnvironment environment(Map<String, Object> variables, String... profiles) throws IOException {
        var environment = new StandardEnvironment();
        var sources = environment.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.addFirst(yaml("application.yml"));
        for (String profile : profiles) {
            sources.addFirst(yaml("application-" + profile + ".yml"));
        }
        sources.addFirst(new MapPropertySource("variables", variables));
        return environment;
    }

    private static PropertySource<?> yaml(String file) throws IOException {
        var loaded = new YamlPropertySourceLoader().load(file, new FileSystemResource(RESOURCES + file));
        assertThat(loaded).hasSize(1);
        return loaded.getFirst();
    }

    private static List<String> settings(StandardEnvironment environment) {
        return List.of(
                environment.getRequiredProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri"),
                environment.getRequiredProperty("openjcockpit.security.jwt-issuer"),
                environment.getRequiredProperty("openjcockpit.git-workspace.service-identity.token-url"),
                environment.getRequiredProperty("openjcockpit.git-workspace.service-identity.client-id"));
    }

    @Test
    void urlRealmAndClientIdDeriveEveryKeycloakSetting() throws IOException {
        var environment = environment(Map.of(
                "KEYCLOAK_URL", "https://auth.example.com",
                "KEYCLOAK_REALM", "acme",
                "KEYCLOAK_CLIENT_ID", "acme-ai-control"));

        assertThat(settings(environment)).containsExactly(
                "https://auth.example.com/realms/acme/protocol/openid-connect/certs",
                "https://auth.example.com/realms/acme",
                "https://auth.example.com/realms/acme/protocol/openid-connect/token",
                "acme-ai-control");
    }

    @Test
    void withoutVariablesTheServiceUsesTheOpenJCockpitRealmOnLocalhost() throws IOException {
        assertThat(settings(environment(Map.of()))).containsExactly(
                "http://localhost:8080/realms/openjcockpit/protocol/openid-connect/certs",
                "http://localhost:8080/realms/openjcockpit",
                "http://localhost:8080/realms/openjcockpit/protocol/openid-connect/token",
                "openjcockpit-ai-control");
    }

    @Test
    void theLocalProfileReachesKeycloakByServiceNameAndAcceptsItsPublicIssuer() throws IOException {
        var environment = environment(Map.of("KEYCLOAK_REALM", "acme"), "local");

        assertThat(settings(environment)).containsExactly(
                "http://keycloak:8080/realms/acme/protocol/openid-connect/certs",
                "http://localhost:8080/realms/acme",
                "http://keycloak:8080/realms/acme/protocol/openid-connect/token",
                "openjcockpit-ai-control");
    }

    @Test
    void keycloakIsOnUnlessDisabled() throws IOException {
        String enabled = SecurityConfig.KEYCLOAK_ENABLED;
        assertThat(environment(Map.of()).getProperty(enabled, Boolean.class)).isTrue();
        assertThat(environment(Map.of("ENABLE_KEYCLOAK", "false")).getProperty(enabled, Boolean.class)).isFalse();
    }

    @Test
    void explicitSettingsOverrideTheDerivedOnes() throws IOException {
        var environment = environment(Map.of(
                "KEYCLOAK_URL", "https://auth.example.com",
                "KEYCLOAK_JWK_SET_URI", "http://keycloak:8080/certs",
                "KEYCLOAK_ISSUER", "https://issuer.example.com",
                "AI_CONTROL_SERVICE_TOKEN_URL", "http://keycloak:8080/token",
                "AI_CONTROL_SERVICE_CLIENT_ID", "legacy-ai-control"));

        assertThat(settings(environment)).containsExactly(
                "http://keycloak:8080/certs",
                "https://issuer.example.com",
                "http://keycloak:8080/token",
                "legacy-ai-control");
    }
}
