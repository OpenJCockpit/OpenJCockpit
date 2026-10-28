package nl.metafactory.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigGuardTest {

    private static MockEnvironment prod() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("openjcockpit.production-guard.required-secrets", "db.password, enc.secret");
        env.setProperty("openjcockpit.production-guard.https-urls", "issuer");
        env.setProperty("db.password", "Zq8v3LrT1mWx9cYp");
        env.setProperty("enc.secret", "pV7nKd2sQ9fHj4LmRt6wXa1Bc8Ye3Gu0");
        env.setProperty("issuer", "https://auth.example.org/realms/openjcockpit");
        return env;
    }

    @Test
    void aCompleteProductionConfigurationStarts() {
        assertThat(ProductionConfigGuard.violations(prod())).isEmpty();
        assertThatCode(() -> new ProductionConfigGuard(prod()).afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void theLocalProfileIsRejectedNextToProd() {
        MockEnvironment env = prod();
        env.setActiveProfiles("prod", "local");
        assertThat(ProductionConfigGuard.violations(env)).containsExactly("profile 'local' must not be active together with 'prod'");
    }

    @Test
    void missingShortAndPlaceholderSecretsAreRejectedWithoutDisclosingValues() {
        MockEnvironment env = prod();
        env.setProperty("enc.secret", "prod-change-this-32chars-minimum!");
        env.setProperty("openjcockpit.production-guard.required-secrets", "db.password,enc.secret,missing");
        env.setProperty("db.password", "keycloak");

        assertThatThrownBy(() -> new ProductionConfigGuard(env).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db.password is shorter than 16 characters")
                .hasMessageContaining("enc.secret is a development placeholder")
                .hasMessageContaining("missing is not set")
                .hasMessageNotContaining("change-this");
    }

    @Test
    void seededLocalSecretsAreRecognisedAsPlaceholders() {
        for (String seeded : new String[]{"sk-openjcockpit-litellm-local", "openjcockpit-agent-service-local-secret",
                "local-only-spec-queue-runner-secret-CHANGE-ME-outside-local", "dev-only-secret-32chars-minimum!",
                "test-only-secret-32-characters!!", "0123456789abcdef", "a2a5e7c1ce5a17ed"}) {
            assertThat(ProductionConfigGuard.DEV_PLACEHOLDER.matcher(seeded).matches()).as(seeded).isTrue();
        }
    }

    @Test
    void endpointsThatMustBeHttpsAreChecked() {
        MockEnvironment env = prod();
        env.setProperty("issuer", "http://localhost:8080/realms/openjcockpit");
        assertThat(ProductionConfigGuard.violations(env)).containsExactly("issuer must be an https:// URL");
    }
}
