package nl.metafactory.agents.spec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpecTemplateServiceTest {

    private final SpecTemplateService service = new SpecTemplateService();

    @Test
    void classpathContentLoadsEveryBundledTemplate() {
        for (String name : SpecTemplateService.TEMPLATE_FILES) {
            assertThat(service.classpathContent(name)).isNotBlank();
        }
        assertThat(service.classpathContent("feature-spec.template.md"))
                .contains("workflow_id: WF-001")
                .contains("# Feature Spec: {{feature_name}}");
    }

    @Test
    void classpathContentThrowsForUnknownResource() {
        assertThatThrownBy(() -> service.classpathContent("does-not-exist.md"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does-not-exist.md");
    }

    @Test
    void templateContentPrefersTemplateOnDisk(@TempDir Path baseDir) throws Exception {
        Path templatesDir = baseDir.resolve(SpecTemplateService.TEMPLATES_DIR);
        Files.createDirectories(templatesDir);
        Files.writeString(templatesDir.resolve("plan.template.md"), "# Custom plan");

        assertThat(service.templateContent(baseDir, "plan.template.md")).isEqualTo("# Custom plan");
    }

    @Test
    void templateContentFallsBackToClasspathWhenDiskFileMissing(@TempDir Path baseDir) {
        assertThat(service.templateContent(baseDir, "plan.template.md"))
                .isEqualTo(service.classpathContent("plan.template.md"));
    }

    @Test
    void templateContentWrapsUnreadableDiskFileInUncheckedIOException(@TempDir Path baseDir) throws Exception {
        Path templatesDir = baseDir.resolve(SpecTemplateService.TEMPLATES_DIR);
        Files.createDirectories(templatesDir);
        Path file = templatesDir.resolve("plan.template.md");
        Files.writeString(file, "# Custom plan");
        Files.setPosixFilePermissions(file, Set.of());
        try {
            assertThatThrownBy(() -> service.templateContent(baseDir, "plan.template.md"))
                    .isInstanceOf(UncheckedIOException.class);
        } finally {
            Files.setPosixFilePermissions(file, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        }
    }

    @Test
    void classpathContentWrapsReadFailuresInUncheckedIOException() {
        var failingService = new SpecTemplateService() {
            @Override
            java.io.InputStream openResource(String name) {
                return new java.io.InputStream() {
                    @Override
                    public int read() throws java.io.IOException {
                        throw new java.io.IOException("read failed");
                    }
                };
            }
        };

        assertThatThrownBy(() -> failingService.classpathContent("plan.template.md"))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void renderReplacesVariablesAndLeavesUnknownPlaceholders() {
        String rendered = service.render("# {{feature_name}} — {{scenario_name}}",
                Map.of("feature_name", "Example Feature"));

        assertThat(rendered).isEqualTo("# Example Feature — {{scenario_name}}");
    }
}
