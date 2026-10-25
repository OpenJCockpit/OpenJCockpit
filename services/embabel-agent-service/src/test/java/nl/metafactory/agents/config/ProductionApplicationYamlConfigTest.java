package nl.metafactory.agents.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.web.ErrorProperties;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-QA-1 (workflow-approval-gate QA report §9), second follow-up review R-3: closes the one
 * remaining gap the lead identified in the D-QA-1 remediation itself. Both
 * {@link ErrorResponseContractIntegrationTest} and ai-control-service's
 * {@code EmbabelErrorRelayIntegrationTest} inject {@code server.error.include-message=always}
 * themselves via {@code @TestPropertySource}, because Maven's test classpath places
 * {@code src/test/resources} ahead of {@code src/main/resources}, so the test-scoped
 * {@code application.yml} silently shadows the production one for any test that boots the full
 * Spring context. That means the whole suite would stay green even if someone later deleted
 * {@code server.error.include-message: always} or
 * {@code spring.main.allow-bean-definition-overriding: true} from the REAL, deployed
 * {@code src/main/resources/application.yml} — a silent D-QA-1 regression with zero test failure.
 *
 * <p>This test reads that file directly off disk via a {@link FileSystemResource}, bypassing the
 * classpath (and therefore the shadowing) entirely, and binds it into
 * {@link org.springframework.boot.autoconfigure.web.ErrorProperties} at both the
 * {@code server.error} and {@code spring.web.error} prefixes — not a hand-rolled string comparison —
 * so it fails the same way a real misconfiguration would be diagnosed.
 */
class ProductionApplicationYamlConfigTest {

    // Surefire runs with the module root as working directory (see SpecRepositoryStructureTest).
    private final Path productionYaml = Path.of(System.getProperty("user.dir"), "src", "main", "resources", "application.yml");

    private Properties loadProductionYaml() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new FileSystemResource(productionYaml));
        Properties properties = factory.getObject();
        assertThat(properties)
                .as("real production application.yml must exist and parse at " + productionYaml)
                .isNotNull();
        return properties;
    }

    @Test
    void productionYamlKeepsIncludeMessageAlwaysSoErrorResponsesRetainTheirMessageField() {
        Properties properties = loadProductionYaml();

        // server.error.include-message is legacy/inert in Boot 4.1.1 (ServerProperties.getError()
        // was removed), but the key remains in the yaml for documentation purposes. We still assert
        // it binds to ALWAYS so the file stays self-documenting.
        ErrorProperties serverErrorProps = new Binder(new MapConfigurationPropertySource(properties))
                .bind("server.error", ErrorProperties.class)
                .orElseThrow(() -> new AssertionError("server.error.* did not bind from the real production application.yml"));

        assertThat(serverErrorProps.getIncludeMessage())
                .as("D-QA-1 regression guard (legacy): server.error.include-message must stay ALWAYS "
                        + "in the real embabel-agent-service application.yml for documentation purposes; "
                        + "this key no longer controls the HTTP response body in Boot 4.1.1")
                .isEqualTo(ErrorProperties.IncludeAttribute.ALWAYS);

        // spring.web.error.include-message is the operative key in Boot 4.1.1 that actually controls
        // the real HTTP error response body (WebProperties @ConfigurationProperties("spring.web")
        // has a nested ErrorProperties field). We assert it too so both paths are covered.
        ErrorProperties webErrorProps = new Binder(new MapConfigurationPropertySource(properties))
                .bind("spring.web.error", ErrorProperties.class)
                .orElseThrow(() -> new AssertionError("spring.web.error.* did not bind from the real production application.yml"));

        assertThat(webErrorProps.getIncludeMessage())
                .as("D-QA-1 regression guard (operative): spring.web.error.include-message must stay ALWAYS "
                        + "in Boot 4.1.1 — this is the key that actually controls the HTTP error response body")
                .isEqualTo(ErrorProperties.IncludeAttribute.ALWAYS);
    }

    @Test
    void productionYamlNoLongerSetsBeanDefinitionOverridingSinceErrorHandlingConfigWasDeleted() {
        Properties properties = loadProductionYaml();

        // ErrorHandlingConfig was deleted because Embabel 1.5.1 ships no competing ErrorController,
        // so the override flag that used to be required for it to register a same-named replacement
        // bean is no longer needed.
        assertThat(properties.getProperty("spring.main.allow-bean-definition-overriding"))
                .as("D-QA-1 regression guard: spring.main.allow-bean-definition-overriding must be "
                        + "absent from the real embabel-agent-service application.yml now that "
                        + "ErrorHandlingConfig has been removed — Embabel 1.5.1 no longer ships a "
                        + "competing ErrorController, so bean-definition overriding is unnecessary")
                .isNull();
    }

}
