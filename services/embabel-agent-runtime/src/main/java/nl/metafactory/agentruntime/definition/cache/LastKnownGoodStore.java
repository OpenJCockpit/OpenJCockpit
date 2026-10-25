package nl.metafactory.agentruntime.definition.cache;

import nl.metafactory.common.agentdefinition.AgentMarkdownParser;
import nl.metafactory.common.agentdefinition.Identifiers;
import nl.metafactory.common.agentdefinition.InvalidDefinitionException;
import nl.metafactory.common.agentdefinition.MarkdownFrontmatter;
import nl.metafactory.common.agentdefinition.AgentDefinitionDocument;
import nl.metafactory.agentruntime.definition.model.DefinitionSource;
import nl.metafactory.agentruntime.definition.model.ProviderResult;
import nl.metafactory.common.agentdefinition.SourceStatus;
import nl.metafactory.agentruntime.definition.source.AgentDefinitionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * File-backed last-known-good snapshots of <em>remote</em> definitions that passed full
 * resolution. Each entry records origin, version, SHA-256 of the raw content, store and expiry
 * time. On read the hash is recomputed and the content re-parsed; a corrupt entry is deleted and
 * reported {@code UNAVAILABLE}, an expired one deleted and reported {@code NOT_FOUND}.
 *
 * <p>The snapshot only replaces the definition <em>content</em>. Tenant binding, tool allowlist,
 * guardrails and model aliases are re-validated by the resolver on every run against current
 * state, so a cached entry can never carry revoked rights or outdated mandatory guardrails.
 */
public class LastKnownGoodStore implements AgentDefinitionProvider {

    private static final Logger log = LoggerFactory.getLogger(LastKnownGoodStore.class);

    record Entry(String agentId, String version, String contentHash, DefinitionSource origin, Instant storedAt,
                 Instant expiresAt, String raw) {
    }

    private final Path directory;
    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;
    private final AgentMarkdownParser parser;
    private final JsonMapper json = JsonMapper.builder().build();

    public LastKnownGoodStore(Path directory, Duration ttl, int maxEntries, Clock clock, AgentMarkdownParser parser) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.directory = directory;
        this.ttl = ttl;
        this.maxEntries = maxEntries;
        this.clock = clock;
        this.parser = parser;
    }

    @Override
    public DefinitionSource source() {
        return DefinitionSource.LAST_KNOWN_GOOD;
    }

    /** Stores a fully validated remote document. Documents from other sources are ignored. */
    public void store(AgentDefinitionDocument doc, DefinitionSource origin) {
        if (origin != DefinitionSource.REMOTE_LITELLM || doc.rawMarkdown() == null || !Identifiers.isSlug(doc.id())) {
            return;
        }
        Instant now = clock.instant();
        var entry = new Entry(doc.id(), doc.version(), doc.contentHash(), origin, now, now.plus(ttl), doc.rawMarkdown());
        try {
            Path tmp = Files.createTempFile(directory, doc.id(), ".tmp");
            Files.write(tmp, json.writeValueAsBytes(entry));
            Files.move(tmp, file(doc.id()), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            evictBeyondCapacity();
            log.info("agent.definition.lkg.stored agentId={} version={} hash={}", doc.id(), doc.version(),
                    doc.contentHash().substring(0, 12));
        } catch (IOException e) {
            log.warn("agent.definition.lkg.store_failed agentId={} error={}", doc.id(), e.getClass().getSimpleName());
        }
    }

    @Override
    public ProviderResult lookup(String agentId) {
        if (!Identifiers.isSlug(agentId)) {
            return ProviderResult.of(SourceStatus.INVALID, "invalid agent id");
        }
        Path file = file(agentId);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return ProviderResult.of(SourceStatus.NOT_FOUND, "no last-known-good snapshot");
        }
        Entry entry;
        try {
            entry = json.readValue(Files.readAllBytes(file), Entry.class);
        } catch (IOException | JacksonException e) {
            return discard(agentId, SourceStatus.UNAVAILABLE, "snapshot unreadable");
        }
        if (entry == null || entry.raw() == null || !agentId.equals(entry.agentId())
                || !MarkdownFrontmatter.sha256(entry.raw()).equals(entry.contentHash())) {
            return discard(agentId, SourceStatus.UNAVAILABLE, "snapshot failed integrity check");
        }
        if (entry.expiresAt() == null || !clock.instant().isBefore(entry.expiresAt())) {
            return discard(agentId, SourceStatus.NOT_FOUND, "snapshot expired");
        }
        try {
            var doc = parser.parse(entry.raw());
            if (!doc.id().equals(agentId) || !doc.version().equals(entry.version())) {
                return discard(agentId, SourceStatus.UNAVAILABLE, "snapshot metadata mismatch");
            }
            return ProviderResult.found(doc);
        } catch (InvalidDefinitionException e) {
            return discard(agentId, SourceStatus.UNAVAILABLE, "snapshot no longer parses");
        }
    }

    public void invalidate(String agentId) {
        if (Identifiers.isSlug(agentId)) {
            deleteQuietly(file(agentId));
        }
    }

    public void invalidateAll() {
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(p -> p.toString().endsWith(".json")).forEach(LastKnownGoodStore::deleteQuietly);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ProviderResult discard(String agentId, SourceStatus status, String reason) {
        log.warn("agent.definition.lkg.rejected agentId={} reason={}", agentId, reason);
        invalidate(agentId);
        return ProviderResult.of(status, reason);
    }

    private void evictBeyondCapacity() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            var all = files.filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(LastKnownGoodStore::modified).reversed()).toList();
            all.stream().skip(maxEntries).forEach(LastKnownGoodStore::deleteQuietly);
        }
    }

    private Path file(String agentId) {
        return directory.resolve(agentId + ".json");
    }

    static java.nio.file.attribute.FileTime modified(Path p) {
        try {
            return Files.getLastModifiedTime(p);
        } catch (IOException e) {
            return java.nio.file.attribute.FileTime.fromMillis(0);
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // next read re-validates anyway
        }
    }
}
