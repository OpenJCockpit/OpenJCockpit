package nl.metafactory.agents.catalog;

import nl.metafactory.agents.definition.markdown.AgentMarkdownParser;
import nl.metafactory.agents.definition.markdown.InvalidDefinitionException;
import nl.metafactory.agents.definition.source.ConfinedRoot;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Lists every agent Markdown file below the repository's {@code /agents} folder, recursively. Each
 * file uses the SPEC-002 agent contract (YAML frontmatter + instructions). Subfolders become the
 * display hierarchy. Invalid files are listed with their problem instead of being hidden; symlinks
 * are never followed; {@code README.md} files are documentation and skipped.
 */
@Component
public class LocalAgentCatalog {

    static final int MAX_DEPTH = 8;
    static final int MAX_FILES = 500;

    private final AgentCatalogProperties properties;
    private final AgentMarkdownParser parser;

    public LocalAgentCatalog(AgentCatalogProperties properties, AgentMarkdownParser parser) {
        this.properties = properties;
        this.parser = parser;
    }

    public AgentCatalog.LocalSection list() {
        Path configured = Path.of(properties.getLocalPath());
        if (!Files.isDirectory(configured)) {
            return new AgentCatalog.LocalSection("Local", "NOT_CONFIGURED",
                    "Local agents folder not found: " + properties.getLocalPath(), List.of());
        }
        var root = new ConfinedRoot(configured).path();
        var agents = new ArrayList<AgentCatalog.LocalAgent>();
        // Without FOLLOW_LINKS, walk never descends into a symlinked folder; symlinked files are filtered below.
        try (Stream<Path> walk = Files.walk(root, MAX_DEPTH, new FileVisitOption[0])) {
            var files = walk
                    .filter(p -> !Files.isSymbolicLink(p))
                    .filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .filter(p -> !p.getFileName().toString().equalsIgnoreCase("README.md"))
                    .sorted(Comparator.comparing(p -> root.relativize(p).toString()))
                    .limit(MAX_FILES)
                    .toList();
            for (Path file : files) {
                agents.add(describe(root, file));
            }
        } catch (IOException | UncheckedIOException e) {
            return new AgentCatalog.LocalSection("Local", "UNAVAILABLE", "Local agents folder is not readable", List.of());
        }
        return new AgentCatalog.LocalSection("Local", "OK", null, markDuplicates(agents));
    }

    private AgentCatalog.LocalAgent describe(Path root, Path file) {
        Path relative = root.relativize(file);
        String path = relative.toString().replace('\\', '/');
        var folders = new ArrayList<String>();
        for (int i = 0; i < relative.getNameCount() - 1; i++) {
            folders.add(relative.getName(i).toString());
        }
        String fallbackId = file.getFileName().toString().replaceFirst("(?i)\\.md$", "");
        try {
            var doc = parser.parse(ConfinedRoot.readBounded(file));
            return new AgentCatalog.LocalAgent(doc.id(), doc.name() != null ? doc.name() : doc.id(), doc.description(),
                    doc.version(), doc.model(), path, List.copyOf(folders), doc.skills(), doc.tools(), true, null);
        } catch (InvalidDefinitionException e) {
            return new AgentCatalog.LocalAgent(fallbackId, fallbackId, null, null, null, path, List.copyOf(folders),
                    List.of(), List.of(), false, e.getMessage());
        }
    }

    private static List<AgentCatalog.LocalAgent> markDuplicates(List<AgentCatalog.LocalAgent> agents) {
        var counts = new HashMap<String, Integer>();
        agents.stream().filter(AgentCatalog.LocalAgent::valid).forEach(a -> counts.merge(a.id(), 1, Integer::sum));
        return agents.stream().map(a -> a.valid() && counts.get(a.id()) > 1
                ? new AgentCatalog.LocalAgent(a.id(), a.name(), a.description(), a.version(), a.model(), a.path(),
                a.folders(), a.skills(), a.tools(), false, "duplicate agent id " + a.id())
                : a).toList();
    }
}
