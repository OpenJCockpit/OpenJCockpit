package nl.metafactory.agentruntime.definition.skill;

import nl.metafactory.common.agentdefinition.Identifiers;
import nl.metafactory.common.agentdefinition.InvalidDefinitionException;
import nl.metafactory.common.agentdefinition.MarkdownFrontmatter;
import nl.metafactory.agentruntime.definition.model.DefinitionSource;
import nl.metafactory.common.agentdefinition.SourceStatus;
import nl.metafactory.common.agentdefinition.ConfinedRoot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Discovers {@code skills/<id>/SKILL.md} packages below the confined definition root. Discovery
 * only: nothing is activated here. Non-conforming folders are skipped and logged by id.
 */
public class LocalSkillInventory implements SkillPackageProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalSkillInventory.class);

    static final int MAX_RESOURCES = 64;
    static final int MAX_RESOURCE_DEPTH = 4;
    static final long MAX_PACKAGE_BYTES = 1024 * 1024;
    /** Only text resources are loaded; anything else (binaries, scripts) is never read. */
    static final List<String> TEXT_RESOURCE_SUFFIXES = List.of(".md", ".txt", ".json", ".yaml", ".yml", ".csv");

    private final ConfinedRoot root;
    private final SkillMarkdownParser parser;

    public LocalSkillInventory(ConfinedRoot root, SkillMarkdownParser parser) {
        this.root = root;
        this.parser = parser;
    }

    @Override
    public DefinitionSource source() {
        return DefinitionSource.LOCAL;
    }

    /** All conforming skills, keyed by id. */
    public Map<String, SkillPackage> discover() {
        var found = new TreeMap<String, SkillPackage>();
        var skillsDir = root.resolve("skills");
        if (skillsDir.isEmpty() || !Files.isDirectory(skillsDir.get(), LinkOption.NOFOLLOW_LINKS)) {
            return Map.of();
        }
        try (Stream<Path> entries = Files.list(skillsDir.get())) {
            for (Path dir : entries.sorted().toList()) {
                String id = dir.getFileName().toString();
                var lookup = lookup(id);
                if (lookup.status() == SourceStatus.FOUND) {
                    found.put(id, lookup.skill());
                } else {
                    log.warn("skill.package.skipped source=LOCAL status={} reason={}", lookup.status(), lookup.reason());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Map.copyOf(found);
    }

    @Override
    public SkillLookup lookup(String skillId) {
        if (!Identifiers.isSlug(skillId)) {
            return SkillLookup.of(SourceStatus.INVALID, "skill id is not a valid identifier");
        }
        try {
            var dir = root.resolve("skills", skillId);
            if (dir.isEmpty()) {
                return SkillLookup.of(SourceStatus.NOT_FOUND, "no local skill " + skillId);
            }
            if (!Files.isDirectory(dir.get(), LinkOption.NOFOLLOW_LINKS)) {
                return SkillLookup.of(SourceStatus.INVALID, "skill " + skillId + " is not a directory");
            }
            var skillFile = root.resolve("skills", skillId, "SKILL.md");
            if (skillFile.isEmpty()) {
                return SkillLookup.of(SourceStatus.INVALID, "skill " + skillId + " has no SKILL.md");
            }
            String raw = ConfinedRoot.readBounded(skillFile.get());
            var parsed = parser.parse(raw, skillId);
            return SkillLookup.found(load(dir.get(), parsed, raw));
        } catch (InvalidDefinitionException e) {
            return SkillLookup.of(SourceStatus.INVALID, "skill " + skillId + ": " + e.getMessage());
        } catch (UncheckedIOException e) {
            return SkillLookup.of(SourceStatus.UNAVAILABLE, "skill " + skillId + " unreadable");
        }
    }

    private SkillPackage load(Path dir, SkillMarkdownParser.ParsedSkill parsed, String raw) {
        var resources = new TreeMap<String, String>();
        var scripts = new ArrayList<String>();
        var hashInput = new StringBuilder(raw);
        long total = raw.length();
        try (Stream<Path> files = Files.walk(dir, MAX_RESOURCE_DEPTH)) {
            for (Path file : files.sorted().toList()) {
                if (Files.isSymbolicLink(file)) {
                    throw new InvalidDefinitionException("symlinks are not allowed inside a skill package");
                }
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || file.getFileName().toString().equals("SKILL.md")
                        && file.getParent().equals(dir)) {
                    continue;
                }
                String relative = dir.relativize(file).toString().replace('\\', '/');
                if (relative.startsWith("scripts/")) {
                    scripts.add(relative);
                    continue;
                }
                if (TEXT_RESOURCE_SUFFIXES.stream().noneMatch(relative::endsWith)) {
                    continue;
                }
                if (resources.size() >= MAX_RESOURCES) {
                    throw new InvalidDefinitionException("skill package has too many resources");
                }
                String content = ConfinedRoot.readBounded(file);
                total += content.length();
                if (total > MAX_PACKAGE_BYTES) {
                    throw new InvalidDefinitionException("skill package exceeds " + MAX_PACKAGE_BYTES + " bytes");
                }
                resources.put(relative, content);
                hashInput.append('\0').append(relative).append('\0').append(content);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new SkillPackage(parsed.name(), parsed.description(), parsed.body(), resources, scripts,
                parsed.allowedTools(), MarkdownFrontmatter.sha256(hashInput.toString()));
    }
}
