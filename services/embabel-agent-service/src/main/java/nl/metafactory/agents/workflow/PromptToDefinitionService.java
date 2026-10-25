package nl.metafactory.agents.workflow;

import nl.metafactory.agents.runtime.AgentRuntimeClient;
import nl.metafactory.agents.workflow.model.AgentSpec;
import nl.metafactory.agents.workflow.model.SkillSpec;
import nl.metafactory.agents.workflow.model.SubagentSpec;
import nl.metafactory.common.agentruntime.DefinitionDrafts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Generates draft agent/subagent/skill definitions from a free-text concept. The LLM call itself runs in
 * embabel-agent-runtime (its default LLM); this service picks the concept and sanitises the draft. When no
 * concept is supplied, a representative example concept is used instead so the button always produces a
 * usable draft.
 */
@Service
public class PromptToDefinitionService {

    private static final String EXAMPLE_AGENT_CONCEPT =
            "A generic example agent for this software delivery platform that coordinates subagents " +
            "to take a specification from intake through to a reviewed, implementation-ready plan.";
    private static final String EXAMPLE_SUBAGENT_CONCEPT =
            "A generic example subagent that performs one well-defined supporting task for its parent " +
            "agent, such as gathering context or validating a single aspect of the work.";
    private static final String EXAMPLE_SKILL_CONCEPT =
            "A generic example reusable skill with a clear input contract and output contract that a " +
            "subagent could invoke as a discrete step.";

    private final AgentRuntimeClient runtime;

    public PromptToDefinitionService(AgentRuntimeClient runtime) {
        this.runtime = runtime;
    }

    public AgentSpec generateAgentSpec(String prompt) {
        String concept = effectiveConcept(prompt, EXAMPLE_AGENT_CONCEPT);
        return sanitizeAgentSpec(draft(DefinitionDrafts.Kind.AGENT, concept, AgentSpec.class), concept);
    }

    public SubagentSpec generateSubagentSpec(String prompt) {
        String concept = effectiveConcept(prompt, EXAMPLE_SUBAGENT_CONCEPT);
        return sanitizeSubagentSpec(draft(DefinitionDrafts.Kind.SUBAGENT, concept, SubagentSpec.class), concept);
    }

    public SkillSpec generateSkillSpec(String prompt) {
        String concept = effectiveConcept(prompt, EXAMPLE_SKILL_CONCEPT);
        return sanitizeSkillSpec(draft(DefinitionDrafts.Kind.SKILL, concept, SkillSpec.class), concept);
    }

    private <T> T draft(DefinitionDrafts.Kind kind, String concept, Class<T> type) {
        try {
            return runtime.draft(kind, concept, type);
        } catch (AgentRuntimeClient.AgentRuntimeException e) {
            throw new ResponseStatusException(AgentRuntimeClient.RUNTIME_UNAVAILABLE.equals(e.reason())
                    ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY, "Definition draft failed: " + e.getMessage());
        }
    }

    private AgentSpec sanitizeAgentSpec(AgentSpec spec, String concept) {
        return new AgentSpec(
                slug(nonBlank(spec.name(), concept)),
                nonBlank(spec.description(), summarize(concept)),
                nonBlank(spec.role(), "generated"),
                nonBlank(spec.instructions(), concept),
                slugList(spec.subagentNames()),
                slugList(spec.skillNames()),
                orEmpty(spec.mcpTools()),
                spec.workflowId(),
                false);
    }

    private SubagentSpec sanitizeSubagentSpec(SubagentSpec spec, String concept) {
        return new SubagentSpec(
                slug(nonBlank(spec.name(), concept)),
                nonBlank(spec.parentAgent(), ""),
                nonBlank(spec.description(), summarize(concept)),
                nonBlank(spec.responsibilities(), ""),
                nonBlank(spec.instructions(), concept),
                slugList(spec.skillNames()),
                orEmpty(spec.mcpTools()),
                spec.workflowId());
    }

    private SkillSpec sanitizeSkillSpec(SkillSpec spec, String concept) {
        return new SkillSpec(
                slug(nonBlank(spec.name(), concept)),
                nonBlank(spec.description(), summarize(concept)),
                nonBlank(spec.inputContract(), ""),
                nonBlank(spec.outputContract(), ""),
                nonBlank(spec.executionInstructions(), concept),
                orEmpty(spec.mcpTools()),
                spec.policyNotes());
    }

    private String effectiveConcept(String prompt, String exampleConcept) {
        return prompt == null || prompt.isBlank() ? exampleConcept : prompt.strip();
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private <T> List<T> orEmpty(List<T> value) {
        return value == null ? List.of() : value;
    }

    private List<String> slugList(List<String> names) {
        if (names == null) return List.of();
        return names.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(this::slug)
                .distinct()
                .toList();
    }

    private String summarize(String text) {
        String trimmed = text == null ? "" : text.strip();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) + "…" : trimmed;
    }

    private String slug(String text) {
        String firstLine = text == null || text.isBlank() ? "" : text.strip().lines().findFirst().orElse("");
        String truncated = firstLine.length() > 60 ? firstLine.substring(0, 60) : firstLine;
        String slug = truncated.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return slug.isBlank() ? "generated-" + UUID.randomUUID().toString().substring(0, 8) : slug;
    }
}
