package nl.metafactory.gitmcp.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpSecurityPropertiesTest {

    private static McpSecurityProperties enabled() {
        var properties = new McpSecurityProperties();
        properties.setKeycloakEnabled("true");
        properties.setIssuerUri("https://auth.example.test/realms/openjcockpit");
        properties.setExpectedAudience("openjcockpit-git-mcp");
        properties.setAllowedAzp("openjcockpit-litellm-git");
        return properties;
    }

    @Test
    void disabledByDefaultAndIgnoresUnusedKeycloakSettings() {
        var properties = new McpSecurityProperties();
        properties.setIssuerUri("not a url");
        properties.setJwkSetUri("::");

        assertThatCode(properties::afterPropertiesSet).doesNotThrowAnyException();
        assertThat(properties.keycloakEnabled()).isFalse();
        assertThat(properties.getKeycloakEnabled()).isEqualTo("false");
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", "True"})
    void acceptsTrueCaseInsensitively(String value) {
        var properties = enabled();
        properties.setKeycloakEnabled(value);

        assertThat(properties.keycloakEnabled()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "yes", "1", "on", " true", "false "})
    void refusesAnyOtherSwitchValueInsteadOfPickingAMode(String value) {
        var properties = enabled();
        properties.setKeycloakEnabled(value);

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KEYCLOAK_ENABLED must be 'true' or 'false'");
    }

    @Test
    void enabledWithCompleteConfigurationStarts() {
        var properties = enabled();
        properties.setJwkSetUri("http://keycloak:8080/realms/openjcockpit/protocol/openid-connect/certs");

        assertThatCode(properties::afterPropertiesSet).doesNotThrowAnyException();
        assertThat(properties.hasJwkSetUri()).isTrue();
        assertThat(properties.getIssuerUri()).isEqualTo("https://auth.example.test/realms/openjcockpit");
        assertThat(properties.getJwkSetUri()).endsWith("/certs");
        assertThat(properties.getExpectedAudience()).isEqualTo("openjcockpit-git-mcp");
        assertThat(properties.getAllowedAzp()).isEqualTo("openjcockpit-litellm-git");
    }

    @Test
    void enabledWithoutAnythingFailsNamingEveryMissingVariable() {
        var properties = new McpSecurityProperties();
        properties.setKeycloakEnabled("true");

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KEYCLOAK_ISSUER_URI is not set")
                .hasMessageContaining("KEYCLOAK_EXPECTED_AUDIENCE is not set")
                .hasMessageContaining("KEYCLOAK_ALLOWED_AZP is not set");
    }

    @Test
    void enabledWithBlankValuesFails() {
        var properties = enabled();
        properties.setIssuerUri(" ");
        properties.setExpectedAudience(" ");
        properties.setAllowedAzp(" , ,");

        assertThatThrownBy(properties::afterPropertiesSet)
                .hasMessageContaining("KEYCLOAK_ISSUER_URI is not set")
                .hasMessageContaining("KEYCLOAK_EXPECTED_AUDIENCE is not set")
                .hasMessageContaining("KEYCLOAK_ALLOWED_AZP is not set");
    }

    @ParameterizedTest
    @ValueSource(strings = {"auth.example.test/realms/x", "ftp://auth.example.test/realms/x", "https:///realms/x", "http://bad host"})
    void enabledWithAnInvalidIssuerOrJwksUrlFails(String url) {
        var issuer = enabled();
        issuer.setIssuerUri(url);
        var jwks = enabled();
        jwks.setJwkSetUri(url);

        assertThatThrownBy(issuer::afterPropertiesSet).hasMessageContaining("KEYCLOAK_ISSUER_URI is not an absolute http(s) URL");
        assertThatThrownBy(jwks::afterPropertiesSet).hasMessageContaining("KEYCLOAK_JWK_SET_URI is not an absolute http(s) URL");
    }

    @Test
    void blankJwkSetUriMeansIssuerDiscovery() {
        var properties = enabled();
        properties.setJwkSetUri(" ");

        assertThatCode(properties::afterPropertiesSet).doesNotThrowAnyException();
        assertThat(properties.hasJwkSetUri()).isFalse();
    }

    @Test
    void allowedAzpIsAnExactTrimmedCommaSeparatedList() {
        var properties = enabled();
        properties.setAllowedAzp(" openjcockpit-litellm-git ,openjcockpit-agent-service,, openjcockpit-litellm-git ");

        assertThat(properties.allowedClientIds())
                .containsExactly("openjcockpit-litellm-git", "openjcockpit-agent-service");
        properties.setAllowedAzp(null);
        assertThat(properties.allowedClientIds()).isEmpty();
    }
}
