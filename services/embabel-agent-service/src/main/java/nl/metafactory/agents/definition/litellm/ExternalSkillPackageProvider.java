package nl.metafactory.agents.definition.litellm;

import nl.metafactory.agents.definition.model.DefinitionSource;
import nl.metafactory.agents.definition.model.SourceStatus;
import nl.metafactory.agents.definition.skill.SkillPackageProvider;

/**
 * Remote skill packages from LiteLLM — {@code UNSUPPORTED} for the pinned LiteLLM v1.101.2.
 * Verified in that tag's source: {@code /v1/skills[/{skill_id}]} is the Anthropic Skills API
 * pass-through; its response model {@code Skill} carries only id, title, version, source and
 * timestamps. LiteLLM-stored skills keep {@code file_content} in its own database for its internal
 * injection hook and expose no download route. The repository's
 * {@code infrastructure/mock-skills-marketplace} is a name/description catalog without content.
 * No archive is therefore downloaded or unpacked; local {@code skills/<id>/SKILL.md} remain the
 * source of skill content.
 */
public class ExternalSkillPackageProvider implements SkillPackageProvider {

    public static final String REASON =
            "LiteLLM v1.101.2 exposes no skill content/archive API (/v1/skills returns metadata only)";

    @Override
    public DefinitionSource source() {
        return DefinitionSource.REMOTE_LITELLM;
    }

    @Override
    public SkillLookup lookup(String skillId) {
        return SkillLookup.of(SourceStatus.UNSUPPORTED, REASON);
    }
}
