package nl.metafactory.agents.spec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ApplicationArguments;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SpecWorkflowInitializerTest {

    private Path baseDir;
    private SpecWorkflowProperties properties;
    private SpecWorkflowInitializer initializer;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        baseDir = tempDir;
        properties = new SpecWorkflowProperties();
        properties.setBasePath(baseDir.toString());
        initializer = new SpecWorkflowInitializer(properties,
                new SpecTemplateService(), new SpecStructureValidator());
    }

    @Test
    void runInitializesFullStructureWhenNothingExists() {
        initializer.run(mock(ApplicationArguments.class));

        for (String template : SpecTemplateService.TEMPLATE_FILES) {
            assertThat(baseDir.resolve("templates/specs/" + template)).isRegularFile();
        }
        assertThat(baseDir.resolve("AGENTS.md")).isRegularFile();
        assertThat(baseDir.resolve("specs/_index.md")).isRegularFile();
        Path feature = baseDir.resolve("specs/001-example-feature");
        assertThat(feature.resolve("spec.md")).isRegularFile();
        assertThat(feature.resolve("plan.md")).isRegularFile();
        assertThat(feature.resolve("tasks.md")).isRegularFile();
        assertThat(feature.resolve("review.md")).isRegularFile();
    }

    @Test
    void runDoesNothingWhenInitDisabled() {
        properties.setInitEnabled(false);

        initializer.run(mock(ApplicationArguments.class));

        assertThat(baseDir.resolve("specs")).doesNotExist();
        assertThat(baseDir.resolve("templates")).doesNotExist();
        assertThat(baseDir.resolve("AGENTS.md")).doesNotExist();
    }

    @Test
    void exampleFeatureIsRenderedFromTemplates() throws Exception {
        initializer.initialize(baseDir);

        String spec = Files.readString(baseDir.resolve("specs/001-example-feature/spec.md"));
        assertThat(spec).startsWith("---");
        assertThat(spec).contains("# Feature Spec: Example Feature");
        assertThat(spec).contains("workflow_id: WF-001");

        String plan = Files.readString(baseDir.resolve("specs/001-example-feature/plan.md"));
        assertThat(plan).contains("# Implementation Plan: Example Feature");

        String index = Files.readString(baseDir.resolve("specs/_index.md"));
        assertThat(index).contains("specs/001-example-feature/spec.md");
    }

    @Test
    void initializeIsIdempotentAndDoesNotDuplicateAgentsRules() throws Exception {
        initializer.initialize(baseDir);
        initializer.initialize(baseDir);

        String agents = Files.readString(baseDir.resolve("AGENTS.md"));
        int occurrences = agents.split(SpecWorkflowInitializer.AGENTS_RULES_MARKER, -1).length - 1;
        assertThat(occurrences).isEqualTo(1);
    }

    @Test
    void rulesAreAppendedToExistingAgentsFileWithoutLosingContent() throws Exception {
        Files.writeString(baseDir.resolve("AGENTS.md"), "# Existing instructions\n\nFollow the house style.\n");

        initializer.initialize(baseDir);

        String agents = Files.readString(baseDir.resolve("AGENTS.md"));
        assertThat(agents).startsWith("# Existing instructions");
        assertThat(agents).contains("Follow the house style.");
        assertThat(agents).contains(SpecWorkflowInitializer.AGENTS_RULES_MARKER);
    }

    @Test
    void rulesAreAppendedWithSeparatorWhenExistingFileHasNoTrailingNewline() throws Exception {
        Files.writeString(baseDir.resolve("AGENTS.md"), "# Existing instructions");

        initializer.initialize(baseDir);

        String agents = Files.readString(baseDir.resolve("AGENTS.md"));
        assertThat(agents).startsWith("# Existing instructions\n\n" + SpecWorkflowInitializer.AGENTS_RULES_MARKER);
    }

    @Test
    void existingValidSpecsFolderIsLeftUntouched() throws Exception {
        initializer.initialize(baseDir);
        Path marker = baseDir.resolve("specs/001-example-feature/spec.md");
        String original = Files.readString(marker);
        Files.writeString(marker, original + "\nManual addition.\n");

        initializer.initialize(baseDir);

        assertThat(Files.readString(marker)).contains("Manual addition.");
    }

    @Test
    void initializeFailsWithClearErrorOnFlatSpecFiles() throws Exception {
        Path specsDir = baseDir.resolve("specs");
        Files.createDirectories(specsDir);
        Files.writeString(specsDir.resolve("customer-chatbot-spec.md"), "# spec");

        assertThatThrownBy(() -> initializer.initialize(baseDir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Spec workflow validation failed")
                .hasMessageContaining("customer-chatbot-spec.md");

        // An existing specs/ folder is not silently migrated or filled in.
        assertThat(specsDir.resolve("001-example-feature")).doesNotExist();
        assertThat(specsDir.resolve("customer-chatbot-spec.md")).isRegularFile();
    }

    @Test
    void customTemplateOnDiskIsUsedForNewFeatureScaffolding() throws Exception {
        Path templatesDir = baseDir.resolve("templates/specs");
        Files.createDirectories(templatesDir);
        Files.writeString(templatesDir.resolve("plan.template.md"), "# Custom plan: {{feature_name}}");

        initializer.initialize(baseDir);

        assertThat(Files.readString(baseDir.resolve("specs/001-example-feature/plan.md")))
                .isEqualTo("# Custom plan: Example Feature");
        // Other templates were filled in from the classpath.
        assertThat(templatesDir.resolve("tasks.template.md")).isRegularFile();
    }

    @Test
    void initializeWrapsIoFailuresInUncheckedIOException() throws Exception {
        Path fileAsBaseDir = baseDir.resolve("not-a-directory");
        Files.writeString(fileAsBaseDir, "regular file");

        assertThatThrownBy(() -> initializer.initialize(fileAsBaseDir))
                .isInstanceOf(UncheckedIOException.class);
    }
}
