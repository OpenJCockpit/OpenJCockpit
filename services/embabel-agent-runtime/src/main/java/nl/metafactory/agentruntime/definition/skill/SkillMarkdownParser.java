package nl.metafactory.agentruntime.definition.skill;

import nl.metafactory.common.agentdefinition.Identifiers;
import nl.metafactory.common.agentdefinition.InvalidDefinitionException;
import nl.metafactory.common.agentdefinition.MarkdownFrontmatter;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parses {@code SKILL.md} per the Agent Skills format: required {@code name} (kebab-case, equal to
 * the directory name) and {@code description} (max 1024 chars); optional {@code license},
 * {@code compatibility}, {@code metadata} and {@code allowed-tools}. Any other key is rejected.
 */
@Component
public class SkillMarkdownParser {

    public record ParsedSkill(String name, String description, String body, List<String> allowedTools) {
    }

    static final Set<String> ALLOWED_KEYS = Set.of("name", "description", "license", "compatibility",
            "metadata", "allowed-tools");

    private final YAMLMapper yaml = YAMLMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    public ParsedSkill parse(String raw, String expectedId) {
        var split = MarkdownFrontmatter.split(raw);
        Map<?, ?> fm;
        try {
            fm = Optional.ofNullable(yaml.readValue(split.yaml(), Map.class))
                    .orElseThrow(() -> new InvalidDefinitionException("SKILL.md frontmatter is empty"));
        } catch (JacksonException e) {
            throw new InvalidDefinitionException("SKILL.md frontmatter is not valid YAML (" + e.getClass().getSimpleName() + ")");
        }
        for (Object key : fm.keySet()) {
            if (!(key instanceof String k) || !ALLOWED_KEYS.contains(k)) {
                throw new InvalidDefinitionException("SKILL.md frontmatter contains an unsupported key");
            }
        }
        if (!(fm.get("name") instanceof String name) || !Identifiers.isSlug(name)) {
            throw new InvalidDefinitionException("SKILL.md name must be lowercase kebab-case");
        }
        if (!name.equals(expectedId)) {
            throw new InvalidDefinitionException("SKILL.md name does not match its directory");
        }
        if (!(fm.get("description") instanceof String description) || description.isBlank()
                || description.length() > 1024) {
            throw new InvalidDefinitionException("SKILL.md description is required (max 1024 chars)");
        }
        if (split.body().isBlank()) {
            throw new InvalidDefinitionException("SKILL.md body is empty");
        }
        Object allowed = fm.get("allowed-tools");
        List<String> allowedTools = allowed instanceof String s && !s.isBlank()
                ? Arrays.stream(s.trim().split("\\s+")).toList()
                : List.of();
        return new ParsedSkill(name, description.strip(), split.body(), allowedTools);
    }
}
