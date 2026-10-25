package nl.metafactory.common.agentruntime;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * LLM-generated drafts for the workflow designer (agent, subagent and skill definitions). The agent service
 * sends a concept; the runtime prompts its default LLM and returns the raw draft, which the agent service
 * sanitises. The draft records are the structured-output schema the LLM fills, field for field the
 * designer's definition model.
 */
public final class DefinitionDrafts {

    public enum Kind {
        AGENT, SUBAGENT, SKILL;

        /** Path segment of {@link AgentRuntimeApi#DEFINITION_DRAFT}. */
        public String pathSegment() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Kind> fromPathSegment(String segment) {
            for (Kind kind : values()) {
                if (kind.pathSegment().equals(segment)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    /** The (already defaulted) concept to draft; never logged. */
    public record Request(String concept) {
    }

    public record ToolRef(String name, String description) {
    }

    public record AgentDraft(String name, String description, String role, String instructions,
                             List<String> subagentNames, List<String> skillNames, List<ToolRef> mcpTools,
                             String workflowId, boolean builtIn) {
    }

    public record SubagentDraft(String name, String parentAgent, String description, String responsibilities,
                                String instructions, List<String> skillNames, List<ToolRef> mcpTools,
                                String workflowId) {
    }

    public record SkillDraft(String name, String description, String inputContract, String outputContract,
                             String executionInstructions, List<ToolRef> mcpTools, String policyNotes) {
    }

    private DefinitionDrafts() {
    }
}
