package nl.metafactory.agentruntime.drafts;

import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import nl.metafactory.common.agentruntime.DefinitionDrafts;
import org.springframework.stereotype.Service;

/**
 * Drafts agent, subagent and skill definitions from a free-text concept with the platform's default LLM.
 * The concept arrives already defaulted; sanitising the draft (slugs, fallbacks) is the agent service's job.
 */
@Service
public class DefinitionDraftService {

    private static final String ROLE = "You are an expert AI agent architect for a software delivery automation platform. ";

    private final Ai ai;

    public DefinitionDraftService(Ai ai) {
        this.ai = ai;
    }

    public Object draft(DefinitionDrafts.Kind kind, String concept) {
        return switch (kind) {
            case AGENT -> ai.withLlm(LlmOptions.withDefaultLlm()).createObject(ROLE
                    + "Given the following concept, produce a structured agent definition with a short, memorable name, "
                    + "a one-sentence description, a short role label, detailed operating instructions for the agent, "
                    + "and lists of related subagent names and skill names it may delegate to (leave lists empty if "
                    + "none are obviously implied). Concept: " + concept, DefinitionDrafts.AgentDraft.class);
            case SUBAGENT -> ai.withLlm(LlmOptions.withDefaultLlm()).createObject(ROLE
                    + "Given the following concept, produce a structured subagent definition with a short, memorable "
                    + "name, the name of a plausible parent agent, a one-sentence description, its responsibilities, "
                    + "detailed operating instructions, and a list of related skill names it may invoke (leave the list "
                    + "empty if none are obviously implied). Concept: " + concept, DefinitionDrafts.SubagentDraft.class);
            case SKILL -> ai.withLlm(LlmOptions.withDefaultLlm()).createObject(ROLE
                    + "Given the following concept, produce a structured skill definition with a short, memorable name, "
                    + "a one-sentence description, its input contract, its output contract, detailed execution "
                    + "instructions, and any policy or validation notes. Concept: " + concept,
                    DefinitionDrafts.SkillDraft.class);
        };
    }
}
