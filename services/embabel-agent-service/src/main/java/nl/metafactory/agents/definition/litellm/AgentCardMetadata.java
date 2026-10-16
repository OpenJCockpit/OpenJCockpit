package nl.metafactory.agents.definition.litellm;

import java.util.List;
import java.util.Objects;

/**
 * Discovery metadata of one agent registered in LiteLLM's A2A Agent Registry (its Agent Card).
 * This is <em>not</em> an executable definition: it carries no instructions, and its skills are
 * A2A capability descriptions, never Embabel skills or tool grants.
 */
public record AgentCardMetadata(
        String litellmAgentId,
        String agentName,
        String description,
        String protocolVersion,
        String url,
        String cardVersion,
        boolean streaming,
        List<CardSkill> skills
) {

    public record CardSkill(String id, String name, String description) {
    }

    public AgentCardMetadata {
        Objects.requireNonNull(litellmAgentId, "litellmAgentId");
        Objects.requireNonNull(agentName, "agentName");
        skills = List.copyOf(skills);
    }
}
