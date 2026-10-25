package nl.metafactory.common.agentdefinition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ConfinedRootTest {

    @Test
    void theRootMustBeAConfiguredDirectory(@TempDir Path dir) throws Exception {
        var file = Files.writeString(dir.resolve("file.md"), "x");

        assertThatThrownBy(() -> new ConfinedRoot(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("root is not configured");
        assertThatThrownBy(() -> new ConfinedRoot(file))
                .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("root is not a directory");
        assertThatThrownBy(() -> new ConfinedRoot(dir.resolve("missing")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("root does not exist");
    }

    @Test
    void symlinksBelowTheRootAreRefused(@TempDir Path dir, @TempDir Path elsewhere) throws Exception {
        Files.writeString(elsewhere.resolve("outside.md"), "x");
        Files.createSymbolicLink(dir.resolve("link.md"), elsewhere.resolve("outside.md"));
        var root = new ConfinedRoot(dir);

        assertThatThrownBy(() -> root.resolve("link.md"))
                .isInstanceOf(InvalidDefinitionException.class).hasMessageContaining("symlinks");
    }

    @Test
    void resolvesExistingFilesAndRejectsSegmentsThatLeaveTheRoot(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.md"), "x");
        var root = new ConfinedRoot(dir);

        assertThat(root.resolve("a.md")).contains(root.path().resolve("a.md"));
        assertThat(root.resolve("missing.md")).isEmpty();
        assertThatThrownBy(() -> root.resolve(".."))
                .isInstanceOf(InvalidDefinitionException.class).hasMessage("illegal path segment");
    }

    @Test
    void readsOnlyBoundedRegularFiles(@TempDir Path dir) throws Exception {
        var big = Files.writeString(dir.resolve("big.md"), "x".repeat(MarkdownFrontmatter.MAX_DOCUMENT_BYTES + 1));

        assertThatThrownBy(() -> ConfinedRoot.readBounded(dir))
                .isInstanceOf(InvalidDefinitionException.class).hasMessage("not a regular file");
        assertThatThrownBy(() -> ConfinedRoot.readBounded(big))
                .isInstanceOf(InvalidDefinitionException.class).hasMessageContaining("exceeds");
        assertThat(ConfinedRoot.readBounded(Files.writeString(dir.resolve("ok.md"), "fine"))).isEqualTo("fine");
    }

    @Test
    void anUnreadableFileIsAnUncheckedIoError(@TempDir Path dir) throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")), "root can read any file");
        var file = Files.writeString(dir.resolve("locked.md"), "x");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));

        assertThatThrownBy(() -> ConfinedRoot.readBounded(file)).isInstanceOf(UncheckedIOException.class);
    }
}
