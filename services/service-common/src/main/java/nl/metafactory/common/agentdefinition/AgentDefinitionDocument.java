package nl.metafactory.common.agentdefinition;

import java.util.List;
import java.util.Objects;

/**
 * A parsed but not yet resolved agent definition, as delivered by one source. This is the
 * application's own Markdown contract (YAML frontmatter + instructions body), not an A2A or Embabel
 * standard. {@code skills} are required; {@code optionalSkills} may be absent at resolution time.
 * {@code name} is an optional display name. {@code rawMarkdown} is the exact source text {@code contentHash} was computed over (kept so a
 * validated document can be stored as a last-known-good snapshot); it is never logged.
 */
public record AgentDefinitionDocument(
        String id,
        String name,
        String version,
        String description,
        String model,
        List<String> skills,
        List<String> optionalSkills,
        List<String> tools,
        List<String> guardrails,
        String instructions,
        String contentHash,
        String rawMarkdown
) {

    public AgentDefinitionDocument {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(instructions, "instructions");
        Objects.requireNonNull(contentHash, "contentHash");
        skills = List.copyOf(skills == null ? List.of() : skills);
        optionalSkills = List.copyOf(optionalSkills == null ? List.of() : optionalSkills);
        tools = List.copyOf(tools == null ? List.of() : tools);
        guardrails = List.copyOf(guardrails == null ? List.of() : guardrails);
    }

    @Override
    public String toString() {
        return "AgentDefinitionDocument{id=" + id + ", version=" + version + ", contentHash=" + contentHash + "}";
    }
}
