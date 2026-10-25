package nl.metafactory.common.agentdefinition;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses the application's agent Markdown contract. Strict on purpose: unknown frontmatter keys,
 * duplicate keys, invalid identifiers and an empty body are all rejected.
 */
public class AgentMarkdownParser {

    record Frontmatter(String id, String name, String version, String description, String model, List<String> skills,
                       List<String> optionalSkills, List<String> tools, List<String> guardrails) {
    }

    private final YAMLMapper yaml = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    public AgentDefinitionDocument parse(String raw) {
        var split = MarkdownFrontmatter.split(raw);
        Frontmatter fm;
        try {
            fm = Optional.ofNullable(yaml.readValue(split.yaml(), Frontmatter.class))
                    .orElseThrow(() -> new InvalidDefinitionException("frontmatter is empty"));
        } catch (JacksonException e) {
            // Deliberately not chaining the parser message: it can echo document content.
            throw new InvalidDefinitionException("frontmatter is not valid for the agent contract ("
                    + e.getClass().getSimpleName() + ")");
        }
        Identifiers.requireSlug(fm.id(), "id");
        requireText(fm.version(), "version");
        requireText(fm.model(), "model");
        if (split.body().isBlank()) {
            throw new InvalidDefinitionException("agent instructions body is empty");
        }
        var skills = Identifiers.requireIds(fm.skills(), Identifiers.SLUG, "skills");
        var optional = Identifiers.requireIds(fm.optionalSkills(), Identifiers.SLUG, "optionalSkills");
        var all = new ArrayList<>(skills);
        all.addAll(optional);
        Identifiers.requireIds(all, Identifiers.SLUG, "skills+optionalSkills");
        if (fm.name() != null && (fm.name().isBlank() || fm.name().length() > 128)) {
            throw new InvalidDefinitionException("name must be 1-128 characters");
        }
        return new AgentDefinitionDocument(fm.id(), fm.name(), fm.version(), fm.description(), fm.model(), skills, optional,
                Identifiers.requireIds(fm.tools(), Identifiers.QUALIFIED, "tools"),
                Identifiers.requireIds(fm.guardrails(), Identifiers.QUALIFIED, "guardrails"),
                split.body(), MarkdownFrontmatter.sha256(raw), raw);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new InvalidDefinitionException(field + " is required");
        }
    }
}
