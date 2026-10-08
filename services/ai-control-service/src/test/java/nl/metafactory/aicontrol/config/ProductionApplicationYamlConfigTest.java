package nl.metafactory.aicontrol.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-QA-1 (workflow-approval-gate QA report §9), second follow-up review R-3: closes the one
 * remaining gap the lead identified in the D-QA-1 remediation itself.
 * {@code EmbabelErrorRelayIntegrationTest} injects {@code server.error.include-message=always}
 * itself via {@code @TestPropertySource}, because Maven's test classpath places
 * {@code src/test/resources} ahead of {@code src/main/resources}, so the test-scoped
 * {@code application.yml} silently shadows the production one for any test that boots the full
 * Spring context. That means the whole suite would stay green even if someone later deleted
 * {@code server.error.include-message: always} from the REAL, deployed
 * {@code src/main/resources/application.yml} — a silent D-QA-1 regression with zero test failure.
 *
 * <p>This test reads that file directly off disk via a {@link FileSystemResource}, bypassing the
 * classpath (and therefore the shadowing) entirely, and binds it into the real
 * {@link ServerProperties} type Spring itself uses — not a hand-rolled string comparison — so it
 * fails the same way a real misconfiguration would be diagnosed.
 */
class ProductionApplicationYamlConfigTest {

    // Surefire runs with the module root as working directory (mirrors the equivalent
    // embabel-agent-service regression guard).
    private final Path productionYaml = Path.of(System.getProperty("user.dir"), "src", "main", "resources", "application.yml");

    @Test
    void productionYamlKeepsIncludeMessageAlwaysSoErrorResponsesRetainTheirMessageField() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new FileSystemResource(productionYaml));
        Properties properties = factory.getObject();
        assertThat(properties)
                .as("real production application.yml must exist and parse at " + productionYaml)
                .isNotNull();

        ServerProperties serverProperties = new Binder(new MapConfigurationPropertySource(properties))
                .bind("server", ServerProperties.class)
                .orElseThrow(() -> new AssertionError("server.* did not bind from the real production application.yml"));

        ErrorProperties errorProperties = new Binder(new MapConfigurationPropertySource(properties))
                .bind("server.error", ErrorProperties.class)
                .orElseThrow(() -> new AssertionError("server.error.* did not bind from the real production application.yml"));

        assertThat(errorProperties.getIncludeMessage())
                .as("D-QA-1 regression guard: server.error.include-message must stay ALWAYS in the "
                        + "real ai-control-service application.yml, otherwise every 400/404/409 "
                        + "response (including the relayed embabel-agent-service messages) silently "
                        + "loses its 'message' field again")
                .isEqualTo(ErrorProperties.IncludeAttribute.ALWAYS);
    }
}
