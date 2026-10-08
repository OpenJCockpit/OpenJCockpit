package nl.metafactory.agents.spec;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the actually checked-in spec structure of this module.
 * Fails the build (mvn test / mvn verify) as soon as someone places loose spec files
 * directly under specs/ or omits a required file.
 */
class SpecRepositoryStructureTest {

    // Surefire runs with the module root as the working directory.
    private final Path moduleRoot = Path.of(System.getProperty("user.dir"));

    @Test
    void committedSpecsFolderFollowsTheSpecDrivenStructure() {
        assertThat(new SpecStructureValidator().validate(moduleRoot.resolve("specs"))).isEmpty();
    }

    @Test
    void committedTemplatesAndAgentsRulesArePresent() throws Exception {
        for (String template : SpecTemplateService.TEMPLATE_FILES) {
            assertThat(moduleRoot.resolve("templates/specs/" + template)).isRegularFile();
        }
        Path agentsFile = moduleRoot.resolve("AGENTS.md");
        assertThat(agentsFile).isRegularFile();
        assertThat(Files.readString(agentsFile)).contains(SpecWorkflowInitializer.AGENTS_RULES_MARKER);
    }
}
