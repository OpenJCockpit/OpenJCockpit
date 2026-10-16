package nl.metafactory.agents.definition.skill;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An Agent Skills package ({@code <id>/SKILL.md} plus resources) loaded fully into memory, so a
 * run's snapshot cannot change underneath it. {@code scriptPaths} are listed for transparency only:
 * scripts are never executed or exposed to the model, and {@code allowedTools} never grants a tool.
 */
public record SkillPackage(
        String id,
        String description,
        String instructions,
        Map<String, String> resources,
        List<String> scriptPaths,
        List<String> declaredAllowedTools,
        String contentHash
) {

    public SkillPackage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(instructions, "instructions");
        Objects.requireNonNull(contentHash, "contentHash");
        resources = Map.copyOf(resources == null ? Map.of() : resources);
        scriptPaths = List.copyOf(scriptPaths == null ? List.of() : scriptPaths);
        declaredAllowedTools = List.copyOf(declaredAllowedTools == null ? List.of() : declaredAllowedTools);
    }

    @Override
    public String toString() {
        return "SkillPackage{id=" + id + ", resources=" + resources.keySet() + ", contentHash=" + contentHash + "}";
    }
}
