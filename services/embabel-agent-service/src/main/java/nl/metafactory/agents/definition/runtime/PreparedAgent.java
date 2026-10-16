package nl.metafactory.agents.definition.runtime;

import com.embabel.agent.api.validation.guardrails.GuardRail;
import nl.metafactory.agents.definition.model.ResolvedAgentDefinition;
import nl.metafactory.agents.definition.skill.SkillPackage;
import nl.metafactory.agents.definition.tool.RegisteredAgentTool;

import java.util.List;
import java.util.Objects;

/**
 * Everything a run needs, produced once by the resolver: the immutable definition snapshot plus
 * the skill packages, bound tools and guardrails it was validated against, plus the definition's
 * public description (used for the A2A Agent Card).
 */
public record PreparedAgent(
        ResolvedAgentDefinition definition,
        List<SkillPackage> skills,
        List<RegisteredAgentTool> tools,
        List<GuardRail> guardRails,
        String description
) {

    public PreparedAgent {
        Objects.requireNonNull(definition, "definition");
        skills = List.copyOf(skills);
        tools = List.copyOf(tools);
        guardRails = List.copyOf(guardRails);
        var skillIds = skills.stream().map(SkillPackage::id).toList();
        if (!skillIds.equals(definition.skillIds())) {
            throw new IllegalArgumentException("skill packages do not match the snapshot");
        }
        var toolIds = tools.stream().map(RegisteredAgentTool::id).toList();
        if (!toolIds.equals(definition.toolIds())) {
            throw new IllegalArgumentException("bound tools do not match the snapshot");
        }
    }
}
