package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;
import nl.metafactory.aicontrol.model.SpecFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SpecFileServiceTest {

    private SpecRepositoryClient specClient;
    private SpecFileService service;

    @BeforeEach
    void setUp() {
        specClient = mock(SpecRepositoryClient.class);
        service = new SpecFileService(specClient);
    }

    @Test
    void resolveSpecFileFindsById() {
        var spec = new SpecFile("spec-001", "build.md", "", "", "Active", true, "content", "");
        when(specClient.listSpecs("*")).thenReturn(List.of(spec));

        SpecFile found = service.resolveSpecFile("spec-001");

        assertThat(found.id()).isEqualTo("spec-001");
    }

    @Test
    void resolveSpecFileFindsbyFileName() {
        var spec = new SpecFile("spec-001", "build.md", "", "", "Active", true, "content", "");
        when(specClient.listSpecs("*")).thenReturn(List.of(spec));

        SpecFile found = service.resolveSpecFile("build.md");

        assertThat(found.fileName()).isEqualTo("build.md");
    }

    @Test
    void resolveSpecFileThrows404WhenNotFound() {
        when(specClient.listSpecs("*")).thenReturn(List.of());

        assertThatThrownBy(() -> service.resolveSpecFile("missing"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void validateSpecFilePassesWithContent() {
        var spec = new SpecFile("id", "file.md", "", "", "", false, "some content", "");
        service.validateSpecFile(spec);
    }

    @Test
    void validateSpecFileThrowsOnBlankContent() {
        var spec = new SpecFile("id", "file.md", "", "", "", false, "  ", "");
        assertThatThrownBy(() -> service.validateSpecFile(spec))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.SPEC_FILE_INVALID));
    }

    @Test
    void validateSpecFileThrowsOnNullContent() {
        var spec = new SpecFile("id", "file.md", "", "", "", false, null, "");
        assertThatThrownBy(() -> service.validateSpecFile(spec))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.SPEC_FILE_INVALID));
    }

    @Test
    void writeSpecToWorkspaceCreatesFileWithContent(@TempDir Path tempDir) throws IOException {
        var spec = new SpecFile("id", "deploy.md", "", "", "", false, "# Spec Content", "");
        Path specDir = tempDir.resolve("spec");

        service.writeSpecToWorkspace(spec, specDir);

        assertThat(specDir.resolve("spec.md")).exists();
        assertThat(Files.readString(specDir.resolve("spec.md"))).isEqualTo("# Spec Content");
    }

    @Test
    void writeSpecToWorkspaceCreatesDirectoryIfMissing(@TempDir Path tempDir) throws IOException {
        var spec = new SpecFile("id", "x.md", "", "", "", false, "hello", "");
        Path specDir = tempDir.resolve("new/nested/dir");

        service.writeSpecToWorkspace(spec, specDir);

        assertThat(specDir).isDirectory();
    }
}