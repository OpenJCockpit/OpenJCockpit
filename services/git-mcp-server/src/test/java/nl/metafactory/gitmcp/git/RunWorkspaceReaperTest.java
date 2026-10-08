package nl.metafactory.gitmcp.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RunWorkspaceReaperTest {

    @TempDir
    Path tempDir;

    private GitToolsProperties properties;
    private RunWorkspaceReaper reaper;

    @BeforeEach
    void setUp() {
        properties = new GitToolsProperties();
        properties.setWorkspaceBasePath(tempDir.toString());
        reaper = new RunWorkspaceReaper(properties);
    }

    @Test
    void reapsStaleRunDirectoryButKeepsFreshOne() throws IOException {
        Path runsDir = tempDir.resolve("runs");
        Path oldRun = runsDir.resolve("old-run");
        Path recentRun = runsDir.resolve("recent-run");

        Files.createDirectories(oldRun);
        Files.createDirectories(recentRun);
        Files.writeString(oldRun.resolve("somefile.txt"), "old");
        Files.writeString(recentRun.resolve("somefile.txt"), "recent");

        // Make the old-run directory older than the retention window.
        FileTime staleTime = FileTime.from(
                Instant.now().minus(properties.getRunWorkspaceRetention()).minusSeconds(60));
        Files.setLastModifiedTime(oldRun, staleTime);

        // Ensure the recent-run directory has a fresh (current) last-modified time.
        Files.setLastModifiedTime(recentRun, FileTime.from(Instant.now()));

        reaper.reapStaleRunWorkspaces();

        assertThat(Files.exists(oldRun)).isFalse();
        assertThat(Files.exists(recentRun)).isTrue();
        assertThat(Files.exists(recentRun.resolve("somefile.txt"))).isTrue();
    }

    @Test
    void neverTouchesLegacyDirectoryDirectlyUnderWorkspaceBasePath() throws IOException {
        Path legacyDir = tempDir.resolve("somehash");
        Files.createDirectories(legacyDir);
        Files.writeString(legacyDir.resolve("somefile.txt"), "legacy");

        FileTime staleTime = FileTime.from(
                Instant.now().minus(properties.getRunWorkspaceRetention()).minusSeconds(60));
        Files.setLastModifiedTime(legacyDir, staleTime);

        reaper.reapStaleRunWorkspaces();

        assertThat(Files.exists(legacyDir)).isTrue();
        assertThat(Files.exists(legacyDir.resolve("somefile.txt"))).isTrue();
    }

    @Test
    void doesNothingWhenRunsDirectoryDoesNotExist() {
        assertThatCode(() -> reaper.reapStaleRunWorkspaces()).doesNotThrowAnyException();
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    void listingFailureIsLoggedAndDoesNotThrow() throws IOException {
        Path runsDir = tempDir.resolve("runs");
        Files.createDirectories(runsDir);

        try {
            // Remove all POSIX permissions so that Files.list(runsDir) throws an IOException.
            Files.setPosixFilePermissions(runsDir, Set.of());

            assertThatCode(() -> reaper.reapStaleRunWorkspaces()).doesNotThrowAnyException();
        } finally {
            // Restore permissions so JUnit's @TempDir cleanup can remove the directory.
            Files.setPosixFilePermissions(runsDir, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    void deleteFailureDuringReapIsLoggedAndDoesNotThrow() throws IOException {
        Path runsDir = tempDir.resolve("runs");
        Path staleRunDir = runsDir.resolve("stale-run");
        Files.createDirectories(staleRunDir);
        Files.writeString(staleRunDir.resolve("child.txt"), "data");

        // Make the stale-run directory older than the retention window.
        FileTime staleTime = FileTime.from(
                Instant.now().minus(properties.getRunWorkspaceRetention()).minusSeconds(60));
        Files.setLastModifiedTime(staleRunDir, staleTime);

        try {
            // Remove write and execute permission so that Files.delete on child.txt fails.
            Files.setPosixFilePermissions(staleRunDir, PosixFilePermissions.fromString("r-xr-xr-x"));

            assertThatCode(() -> reaper.reapStaleRunWorkspaces()).doesNotThrowAnyException();
        } finally {
            // Restore full permissions so @TempDir cleanup can still remove the directory.
            Files.setPosixFilePermissions(staleRunDir, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }
}
