package nl.metafactory.agents.workflow;

import tools.jackson.dataformat.yaml.YAMLMapper;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Small generic file-based store shared by the workflow/agent/subagent/skill definition repositories
 * (and, since the policy feature, the decision-log audit repository too). Each record is one YAML
 * file named "{key}.yaml" inside its own subdirectory.
 *
 * <p>{@code FAIL_ON_UNKNOWN_PROPERTIES} is deliberately disabled (ADR-008, workflow-approval-gate
 * delivery, batch B1): a YAML file written by a newer build (e.g. carrying an {@code approvalGate}
 * key) must still load on an older build that does not know that field, so that a rollback degrades
 * to "field ignored" rather than to a boot-time {@link UncheckedIOException} for every repository
 * built on this store — workflow, group, agent-spec, subagent-spec and skill-spec definitions, and
 * the policy decision-log audit trail. This mirrors the tolerant-by-default posture Spring Boot's
 * own auto-configured {@code ObjectMapper} already takes everywhere else in this codebase.
 */
@Component
public class YamlDefinitionStore {

    private static final Logger LOG = LoggerFactory.getLogger(YamlDefinitionStore.class);

    private final YAMLMapper yamlMapper = YAMLMapper.builder()
            .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // A persisted YAML record written by an older build (or missing a field entirely)
            // may omit a primitive boolean field; default to false rather than fail loading,
            // matching this class's existing tolerant-by-default posture for unknown properties.
            .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    public <T> List<T> list(Path dir, Class<T> type) {
        return scan(dir, type).stream().map(Map.Entry::getValue).toList();
    }

    /**
     * Like {@link #list}, but also exposes the on-disk filename (without extension) each
     * record was read from. Callers that key records by an id field embedded in the record
     * itself use this to detect and repair a mismatch between the two (e.g. a record saved
     * with a blank id, which would otherwise be unreachable by that id going forward).
     */
    public <T> List<Map.Entry<String, T>> listWithKeys(Path dir, Class<T> type) {
        return scan(dir, type);
    }

    public <T> Optional<T> find(Path dir, String key, Class<T> type) {
        Path file = dir.resolve(key + ".yaml");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(read(file, type));
    }

    public <T> T save(Path dir, String key, T value) {
        Path file = dir.resolve(key + ".yaml");
        try {
            Files.createDirectories(dir);
            yamlMapper.writeValue(file.toFile(), value);
            return value;
        } catch (IOException e) {
            String rel = DefinitionFileDiagnostics.relativeName(file);
            LOG.warn("definition.file.write.failed path={}", file.toAbsolutePath(), e);
            throw new UncheckedIOException("Failed to write definition file '" + rel + "': "
                    + DefinitionFileDiagnostics.sanitisedReason(e), e);
        } catch (tools.jackson.core.JacksonException e) {
            String rel = DefinitionFileDiagnostics.relativeName(file);
            LOG.warn("definition.file.write.failed path={}", file.toAbsolutePath(), e);
            throw new UncheckedIOException("Failed to write definition file '" + rel + "': "
                    + DefinitionFileDiagnostics.sanitisedReason(e), new IOException(e));
        }
    }

    public boolean delete(Path dir, String key) {
        Path file = dir.resolve(key + ".yaml");
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            String rel = DefinitionFileDiagnostics.relativeName(file);
            LOG.warn("definition.file.delete.failed path={}", file.toAbsolutePath(), e);
            throw new UncheckedIOException("Failed to delete definition file '" + rel + "': "
                    + DefinitionFileDiagnostics.sanitisedReason(e), e);
        }
    }

    private <T> List<Map.Entry<String, T>> scan(Path dir, Class<T> type) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream.filter(p -> p.toString().endsWith(".yaml")).sorted().toList();
        } catch (IOException e) {
            String rel = DefinitionFileDiagnostics.relativeName(dir);
            LOG.warn("definition.dir.list.failed path={}", dir.toAbsolutePath(), e);
            throw new UncheckedIOException("Failed to list definition directory '" + rel + "'", e);
        }
        var loaded = new ArrayList<Map.Entry<String, T>>(files.size());
        var failed = new ArrayList<String>();
        for (Path file : files) {
            try {
                loaded.add(Map.entry(keyOf(file), read(file, type)));
            } catch (DefinitionFileReadException e) {
                failed.add(e.relativeName());
            }
        }
        summarise(dir, files.size(), loaded.size(), failed);
        return List.copyOf(loaded);
    }

    private void summarise(Path dir, int scanned, int loaded, List<String> failed) {
        if (failed.isEmpty()) {
            LOG.debug("definition.dir.scanned path={} scanned={} loaded={} failed=0", dir.toAbsolutePath(), scanned, loaded);
        } else {
            LOG.warn("definition.dir.scanned path={} scanned={} loaded={} failed={} failures={}",
                    dir.toAbsolutePath(), scanned, loaded, failed.size(), failed);
        }
    }

    private String keyOf(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".yaml".length());
    }

    private <T> T read(Path file, Class<T> type) {
        try {
            T value = yamlMapper.readValue(file.toFile(), type);
            LOG.debug("definition.file.read path={}", file.toAbsolutePath());
            return value;
        } catch (tools.jackson.core.JacksonException e) {
            String rel = DefinitionFileDiagnostics.relativeName(file);
            String reason = DefinitionFileDiagnostics.sanitisedReason(e);
            LOG.warn("definition.file.read.failed path={} reason={}", file.toAbsolutePath(), reason, e);
            throw new DefinitionFileReadException(rel, reason, new IOException(e));
        }
    }
}
