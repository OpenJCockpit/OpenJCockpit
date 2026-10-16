package nl.metafactory.agents.definition.source;

import nl.metafactory.agents.definition.markdown.InvalidDefinitionException;
import nl.metafactory.agents.definition.markdown.MarkdownFrontmatter;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A directory that file reads may never escape: no {@code ..}, no absolute segments and no
 * symlinks (neither the file nor any directory between it and the root).
 */
public final class ConfinedRoot {

    private final Path root;

    public ConfinedRoot(Path configured) {
        if (configured == null) {
            throw new IllegalArgumentException("root is not configured");
        }
        try {
            this.root = configured.toAbsolutePath().normalize().toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("root does not exist: " + configured, e);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("root is not a directory: " + configured);
        }
    }

    public Path path() {
        return root;
    }

    /**
     * Resolves {@code segments} under the root. Returns empty when the target does not exist and
     * throws {@link InvalidDefinitionException} when it would escape the root or crosses a symlink.
     */
    public Optional<Path> resolve(String... segments) {
        Path current = root;
        for (String segment : segments) {
            if (segment.isEmpty() || segment.contains("/") || segment.contains("\\")
                    || segment.equals(".") || segment.equals("..") || segment.indexOf('\0') >= 0
                    || !current.resolve(segment).normalize().startsWith(root)) {
                throw new InvalidDefinitionException("illegal path segment");
            }
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new InvalidDefinitionException("symlinks are not allowed below the definition root");
            }
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                return Optional.empty();
            }
        }
        return Optional.of(current);
    }

    /** Reads a regular file with the definition size cap. */
    public static String readBounded(Path file) {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new InvalidDefinitionException("not a regular file");
        }
        try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = in.readNBytes(MarkdownFrontmatter.MAX_DOCUMENT_BYTES + 1);
            if (bytes.length > MarkdownFrontmatter.MAX_DOCUMENT_BYTES) {
                throw new InvalidDefinitionException("document exceeds " + MarkdownFrontmatter.MAX_DOCUMENT_BYTES + " bytes");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
