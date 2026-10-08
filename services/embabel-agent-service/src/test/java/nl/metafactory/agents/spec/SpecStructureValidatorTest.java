package nl.metafactory.agents.spec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpecStructureValidatorTest {

    private static final String VALID_FRONTMATTER = """
            ---
            workflow_id: WF-001
            spec_id: SPEC-001
            dependencies:
              depends_on: []
            data_profile:
              data_classification: internal
            llm_strategy:
              mode: enterprise_cloud
            risk_profile:
              privacy_risk: low
            validation:
              required_checks: []
            ---

            # Feature Spec: Test
            """;

    private final SpecStructureValidator validator = new SpecStructureValidator();

    private Path specsDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        specsDir = tempDir.resolve("specs");
    }

    private Path writeValidFeature(String folderName) throws Exception {
        Path feature = specsDir.resolve(folderName);
        Files.createDirectories(feature);
        Files.writeString(feature.resolve("spec.md"), VALID_FRONTMATTER);
        Files.writeString(feature.resolve("plan.md"), "# Plan");
        Files.writeString(feature.resolve("tasks.md"), "# Tasks");
        Files.writeString(feature.resolve("review.md"), "# Review");
        return feature;
    }

    @Test
    void validStructurePasses() throws Exception {
        Files.createDirectories(specsDir);
        Files.writeString(specsDir.resolve("_index.md"), "# Spec Index");
        writeValidFeature("001-example-feature");
        writeValidFeature("002-invoice-export");

        assertThat(validator.validate(specsDir)).isEmpty();
    }

    @Test
    void missingSpecsFolderFails() {
        List<String> errors = validator.validate(specsDir);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("specs/ folder is missing");
    }

    @Test
    void flatFeatureFilesDirectlyUnderSpecsFail() throws Exception {
        Files.createDirectories(specsDir);
        Files.writeString(specsDir.resolve("_index.md"), "# Spec Index");
        Files.writeString(specsDir.resolve("foo-spec.md"), "# spec");
        Files.writeString(specsDir.resolve("foo-plan.md"), "# plan");
        Files.writeString(specsDir.resolve("foo-tasks.md"), "# tasks");
        Files.writeString(specsDir.resolve("foo-review.md"), "# review");

        List<String> errors = validator.validate(specsDir);

        assertThat(errors).hasSize(4);
        assertThat(errors).allSatisfy(error -> assertThat(error).contains("forbidden"));
        assertThat(errors).anySatisfy(error -> assertThat(error).contains("foo-spec.md"));
    }

    @Test
    void invalidFeatureFolderNamesFail() throws Exception {
        Files.createDirectories(specsDir.resolve("customer-chatbot"));
        Files.createDirectories(specsDir.resolve("01-too-short"));
        Files.createDirectories(specsDir.resolve("001-Uppercase"));

        List<String> errors = validator.validate(specsDir);

        assertThat(errors).hasSize(3);
        assertThat(errors).allSatisfy(error ->
                assertThat(error).contains("does not match the pattern NNN-feature-slug"));
    }

    @Test
    void missingRequiredFilesFailPerFile() throws Exception {
        Path feature = writeValidFeature("001-example-feature");
        Files.delete(feature.resolve("spec.md"));
        Files.delete(feature.resolve("plan.md"));
        Files.delete(feature.resolve("tasks.md"));
        Files.delete(feature.resolve("review.md"));

        List<String> errors = validator.validate(specsDir);

        assertThat(errors).containsExactlyInAnyOrder(
                "specs/001-example-feature is missing required file: spec.md",
                "specs/001-example-feature is missing required file: plan.md",
                "specs/001-example-feature is missing required file: tasks.md",
                "specs/001-example-feature is missing required file: review.md");
    }

    @Test
    void missingSingleRequiredFileFails() throws Exception {
        Path feature = writeValidFeature("003-agent-routing");
        Files.delete(feature.resolve("review.md"));

        assertThat(validator.validate(specsDir))
                .containsExactly("specs/003-agent-routing is missing required file: review.md");
    }

    @Test
    void specWithoutFrontmatterFails() throws Exception {
        Path feature = writeValidFeature("001-example-feature");
        Files.writeString(feature.resolve("spec.md"), "# Feature Spec without frontmatter");

        assertThat(validator.validate(specsDir))
                .containsExactly("specs/001-example-feature/spec.md is missing YAML frontmatter (file must start with ---)");
    }

    @Test
    void specWithUnclosedFrontmatterFails() throws Exception {
        Path feature = writeValidFeature("001-example-feature");
        Files.writeString(feature.resolve("spec.md"), "---\nworkflow_id: WF-001\n");

        assertThat(validator.validate(specsDir))
                .containsExactly("specs/001-example-feature/spec.md has no closing --- for the YAML frontmatter");
    }

    @Test
    void specMissingRequiredFrontmatterKeysFailsPerKey() throws Exception {
        Path feature = writeValidFeature("001-example-feature");
        Files.writeString(feature.resolve("spec.md"), """
                ---
                workflow_id: WF-001
                spec_id: SPEC-001
                ---
                # Feature Spec
                """);

        List<String> errors = validator.validate(specsDir);

        assertThat(errors).containsExactlyInAnyOrder(
                "specs/001-example-feature/spec.md frontmatter is missing required key: dependencies",
                "specs/001-example-feature/spec.md frontmatter is missing required key: data_profile",
                "specs/001-example-feature/spec.md frontmatter is missing required key: llm_strategy",
                "specs/001-example-feature/spec.md frontmatter is missing required key: risk_profile",
                "specs/001-example-feature/spec.md frontmatter is missing required key: validation");
    }

    @Test
    void unreadableSpecsFolderIsWrappedInUncheckedIOException() throws Exception {
        Files.createDirectories(specsDir);
        Files.setPosixFilePermissions(specsDir, Set.of());
        try {
            assertThatThrownBy(() -> validator.validate(specsDir))
                    .isInstanceOf(UncheckedIOException.class);
        } finally {
            Files.setPosixFilePermissions(specsDir, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        }
    }

    @Test
    void unreadableSpecFileIsWrappedInUncheckedIOException() throws Exception {
        Path feature = writeValidFeature("001-example-feature");
        Path specFile = feature.resolve("spec.md");
        Files.setPosixFilePermissions(specFile, Set.of());
        try {
            assertThatThrownBy(() -> validator.validate(specsDir))
                    .isInstanceOf(UncheckedIOException.class);
        } finally {
            Files.setPosixFilePermissions(specFile, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        }
    }
}
