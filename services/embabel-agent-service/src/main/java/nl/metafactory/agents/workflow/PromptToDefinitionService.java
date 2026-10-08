package nl.metafactory.agents.workflow;

import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import nl.metafactory.agents.workflow.model.AgentSpec;
import nl.metafactory.agents.workflow.model.SkillSpec;
import nl.metafactory.agents.workflow.model.SubagentSpec;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Generates draft agent/subagent/skill definitions from a free-text concept using the
 * platform's default LLM (the same OpenAI model configured for the hardcoded delivery
 * agents, see OpenAiModelConfig). When no concept is supplied, a representative example
 * concept is used instead so the button always produces a usable draft.
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

    private final Ai ai;

    public PromptToDefinitionService(Ai ai) {
        this.ai = ai;
    }

    public AgentSpec generateAgentSpec(String prompt) {
        String concept = effectiveConcept(prompt, EXAMPLE_AGENT_CONCEPT);
        String instruction = "You are an expert AI agent architect for a software delivery automation platform. " +
                "Given the following concept, produce a structured agent definition with a short, memorable name, " +
                "a one-sentence description, a short role label, detailed operating instructions for the agent, " +
                "and lists of related subagent names and skill names it may delegate to (leave lists empty if " +
                "none are obviously implied). Concept: " + concept;
        AgentSpec generated = ai.withLlm(LlmOptions.withDefaultLlm()).createObject(instruction, AgentSpec.class);
        return sanitizeAgentSpec(generated, concept);
    }

    public SubagentSpec generateSubagentSpec(String prompt) {
        String concept = effectiveConcept(prompt, EXAMPLE_SUBAGENT_CONCEPT);
        String instruction = "You are an expert AI agent architect for a software delivery automation platform. " +
                "Given the following concept, produce a structured subagent definition with a short, memorable " +
                "name, the name of a plausible parent agent, a one-sentence description, its responsibilities, " +
                "detailed operating instructions, and a list of related skill names it may invoke (leave the list " +
                "empty if none are obviously implied). Concept: " + concept;
        SubagentSpec generated = ai.withLlm(LlmOptions.withDefaultLlm()).createObject(instruction, SubagentSpec.class);
        return sanitizeSubagentSpec(generated, concept);
    }

    public SkillSpec generateSkillSpec(String prompt) {
        String concept = effectiveConcept(prompt, EXAMPLE_SKILL_CONCEPT);
        String instruction = "You are an expert AI agent architect for a software delivery automation platform. " +
                "Given the following concept, produce a structured skill definition with a short, memorable name, " +
                "a one-sentence description, its input contract, its output contract, detailed execution " +
                "instructions, and any policy or validation notes. Concept: " + concept;
        SkillSpec generated = ai.withLlm(LlmOptions.withDefaultLlm()).createObject(instruction, SkillSpec.class);
        return sanitizeSkillSpec(generated, concept);
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
