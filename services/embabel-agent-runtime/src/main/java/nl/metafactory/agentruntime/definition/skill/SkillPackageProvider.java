package nl.metafactory.agentruntime.definition.skill;

import nl.metafactory.agentruntime.definition.model.DefinitionSource;
import nl.metafactory.common.agentdefinition.SourceStatus;

import java.util.Objects;

/** Maps a skill id to a verified skill package from one kind of source. */
public interface SkillPackageProvider {

    record SkillLookup(SourceStatus status, SkillPackage skill, String reason) {

        public SkillLookup {
            Objects.requireNonNull(status, "status");
            if ((status == SourceStatus.FOUND) != (skill != null)) {
                throw new IllegalArgumentException("a skill is present if and only if status is FOUND");
            }
        }

        public static SkillLookup found(SkillPackage skill) {
            return new SkillLookup(SourceStatus.FOUND, skill, null);
        }

        public static SkillLookup of(SourceStatus status, String reason) {
            return new SkillLookup(status, null, reason);
        }
    }

    DefinitionSource source();

    SkillLookup lookup(String skillId);
}
