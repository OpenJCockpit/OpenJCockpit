package nl.metafactory.agents.config;

import com.embabel.agent.spi.support.LlmDataBindingProperties;
import com.embabel.agent.spi.support.LlmOperationsPromptsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.PropertyPlaceholderHelper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-09 / R16 (architecture): Embabel 1.5.1 has TWO candidate property-binder classes for the
 * {@code embabel.agent.platform.llm-operations.*} configuration tree — the newer
 * {@code AgentPlatformProperties$LlmOperationsConfig$PromptsConfig} does NOT carry
 * {@code defaultTimeout}; only {@link LlmOperationsPromptsProperties} (bound at prefix
 * {@code embabel.agent.platform.llm-operations.prompts}) and {@link LlmDataBindingProperties}
 * (bound at prefix {@code embabel.agent.platform.llm-operations.data-binding}) do. This test
 * measures the ACTUALLY BOUND value from the real production {@code application.yml}, using
 * Spring's real {@link Binder} against the real Embabel property classes — not config-metadata
 * inspection, and not a hand-rolled type — because metadata presence is not proof of a working
 * runtime binding.
 *
 * <p>Reads {@code src/main/resources/application.yml} directly off disk via a
 * {@link FileSystemResource}, bypassing Maven's test-classpath shadowing of
 * {@code src/test/resources/application.yml} (the same technique {@code ProductionApplicationYamlConfigTest}
 * uses and for the same reason).
 */
class LlmOperationsRuntimeBindingTest {

    private final Path productionYaml = Path.of(System.getProperty("user.dir"), "src", "main", "resources", "application.yml");

    private Properties loadProductionYaml() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new FileSystemResource(productionYaml));
        Properties raw = factory.getObject();
        assertThat(raw)
                .as("real production application.yml must exist and parse at " + productionYaml)
                .isNotNull();

        PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}", ":", null, true);
        Properties resolved = new Properties();
        for (String name : raw.stringPropertyNames()) {
            resolved.setProperty(name, helper.replacePlaceholders(raw.getProperty(name), raw::getProperty));
        }
        return resolved;
    }

    @Test
    void llmOperationTimeoutBindsTo600SecondsFromTheRealProductionYaml() {
        Properties properties = loadProductionYaml();

        LlmOperationsPromptsProperties bound = new Binder(new MapConfigurationPropertySource(properties))
                .bind("embabel.agent.platform.llm-operations.prompts", LlmOperationsPromptsProperties.class)
                .orElseThrow(() -> new AssertionError(
                        "embabel.agent.platform.llm-operations.prompts did not bind from the real production application.yml"));

        assertThat(bound.getDefaultTimeout())
                .as("AC-09: LlmOperationsPromptsProperties.getDefaultTimeout() must actually bind to PT600S "
                        + "from the real production application.yml, not merely appear in config metadata")
                .isEqualTo(Duration.ofSeconds(600));
    }

    @Test
    void dataBindingMaxAttemptsBindsTo2FromTheRealProductionYaml() {
        Properties properties = loadProductionYaml();

        LlmDataBindingProperties bound = new Binder(new MapConfigurationPropertySource(properties))
                .bind("embabel.agent.platform.llm-operations.data-binding", LlmDataBindingProperties.class)
                .orElseThrow(() -> new AssertionError(
                        "embabel.agent.platform.llm-operations.data-binding did not bind from the real production application.yml"));

        assertThat(bound.getMaxAttempts())
                .as("AC-09: LlmDataBindingProperties.getMaxAttempts() must actually bind to 2 "
                        + "from the real production application.yml, not merely appear in config metadata")
                .isEqualTo(2);
    }
}
