package nl.metafactory.agentruntime.definition.skill;

import nl.metafactory.common.agentdefinition.AgentDefinitionDocument;
import nl.metafactory.agentruntime.definition.model.AgentResolutionException;
import nl.metafactory.agentruntime.definition.model.ResolutionFailure;
import nl.metafactory.common.agentdefinition.SourceStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the skills an agent references against the configured providers (in order).
 *
 * <p>Contract: every entry of {@code skills} is required and must resolve. An entry of
 * {@code optionalSkills} that no provider has ({@code NOT_FOUND}) is omitted and reported; one
 * that exists but is {@code INVALID} or {@code UNAUTHORIZED} still blocks the run, because a broken
 * package must never be silently ignored. A later provider is only consulted when the earlier one
 * reported {@code NOT_FOUND}, {@code UNAVAILABLE} or {@code UNSUPPORTED}.
 */
public class SkillResolver {

    public record ResolvedSkills(List<SkillPackage> skills, List<String> omittedOptional) {

        public ResolvedSkills {
            skills = List.copyOf(skills);
            omittedOptional = List.copyOf(omittedOptional);
        }
    }

    private final List<SkillPackageProvider> providers;

    public SkillResolver(List<SkillPackageProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public ResolvedSkills resolve(AgentDefinitionDocument agent) {
        var skills = new ArrayList<SkillPackage>();
        var omitted = new ArrayList<String>();
        for (String id : agent.skills()) {
            var lookup = lookup(id);
            if (lookup.status() != SourceStatus.FOUND) {
                throw failure(agent, id, lookup, "required");
            }
            skills.add(lookup.skill());
        }
        for (String id : agent.optionalSkills()) {
            var lookup = lookup(id);
            switch (lookup.status()) {
                case FOUND -> skills.add(lookup.skill());
                case NOT_FOUND, UNSUPPORTED -> omitted.add(id);
                default -> throw failure(agent, id, lookup, "optional");
            }
        }
        return new ResolvedSkills(skills, omitted);
    }

    private SkillPackageProvider.SkillLookup lookup(String id) {
        SkillPackageProvider.SkillLookup last = SkillPackageProvider.SkillLookup.of(SourceStatus.NOT_FOUND,
                "no skill provider configured");
        for (SkillPackageProvider provider : providers) {
            last = provider.lookup(id);
            if (!last.status().isFallbackCandidate()) {
                return last;
            }
        }
        // Every provider failed softly; an unreachable source must not look like "optional, absent".
        return last.status() == SourceStatus.UNSUPPORTED ? SkillPackageProvider.SkillLookup.of(SourceStatus.NOT_FOUND,
                last.reason()) : last;
    }

    private static AgentResolutionException failure(AgentDefinitionDocument agent, String skillId,
                                                    SkillPackageProvider.SkillLookup lookup, String kind) {
        var failure = switch (lookup.status()) {
            case UNAUTHORIZED -> ResolutionFailure.UNAUTHORIZED;
            case INVALID -> ResolutionFailure.INVALID;
            default -> ResolutionFailure.SKILL_MISSING;
        };
        return new AgentResolutionException(failure, "agent " + agent.id() + " " + kind + " skill " + skillId
                + " is " + lookup.status());
    }
}
