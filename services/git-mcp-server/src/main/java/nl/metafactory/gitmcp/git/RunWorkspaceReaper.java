package nl.metafactory.gitmcp.git;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically reaps stale per-run workspace directories.
 *
 * <p>Each workflow run is materialised under {@code <workspaceBasePath>/runs/<runId>}. This
 * component walks the immediate child directories of that {@code runs/} directory and
 * recursively deletes any child whose last-modified time is older than the configured
 * retention window ({@link GitToolsProperties#getRunWorkspaceRetention()}).
 *
 * <p>The reaper is intentionally scoped to the {@code runs/} subtree only. It never inspects,
 * lists, or deletes anything outside that directory, and in particular it never touches the
 * legacy shared-clone directory that lives directly under {@code workspaceBasePath}.
 *
 * <p>Failures while listing or reaping a single child directory are logged and do not prevent
 * the remaining directories from being processed.
 */
@Component
public class RunWorkspaceReaper {

    private static final Logger log = LoggerFactory.getLogger(RunWorkspaceReaper.class);

    private final GitToolsProperties properties;

    /**
     * Creates a new reaper bound to the supplied Git MCP properties.
     *
     * @param properties the Git MCP tool properties providing the workspace base path and
     *                   retention/reaping configuration
     */
    public RunWorkspaceReaper(GitToolsProperties properties) {
        this.properties = properties;
    }

    /**
     * Reaps stale run workspace directories.
     *
     * <p>Resolves {@code <workspaceBasePath>/runs} and, if it exists as a directory, deletes
     * every immediate child directory whose last-modified time is older than the configured
     * retention window. The method is a no-op when the {@code runs/} directory does not exist.
     *
     * <p>This method never touches anything outside the {@code runs/} directory, including the
     * legacy shared-clone path directly under {@code workspaceBasePath}.
     */
    @Scheduled(fixedDelayString = "${openjcockpit.git-mcp.run-workspace-reap-interval:PT1H}")
    public void reapStaleRunWorkspaces() {
        Path runsDirectory = Path.of(properties.getWorkspaceBasePath()).resolve("runs");
        if (!Files.isDirectory(runsDirectory)) {
            return;
        }

        Duration retention = properties.getRunWorkspaceRetention();
        Instant cutoff = Instant.now().minus(retention);

        try (Stream<Path> children = Files.list(runsDirectory)) {
            children
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(child -> reapIfStale(child, cutoff));
        } catch (IOException e) {
            log.warn("Failed to list run workspace directories under {}: {}", runsDirectory, e.getMessage());
        }
    }

    private void reapIfStale(Path child, Instant cutoff) {
        try {
            Instant lastModified = Files.getLastModifiedTime(child).toInstant();
            if (lastModified.isAfter(cutoff)) {
                return;
            }
            log.info("Reaping stale run workspace directory: {}", child);
            deleteRecursively(child);
        } catch (IOException | UncheckedIOException e) {
            String detail = (e instanceof UncheckedIOException uioe) ? uioe.getCause().getMessage() : e.getMessage();
            log.warn("Failed to reap run workspace directory {}: {}", child, detail);
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            walk
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }
}
