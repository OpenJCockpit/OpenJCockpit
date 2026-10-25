package nl.metafactory.agentruntime.definition.model;

import nl.metafactory.common.agentdefinition.SourceStatus;
import nl.metafactory.common.agentdefinition.AgentDefinitionDocument;
import nl.metafactory.agentruntime.definition.skill.SkillPackage;
import nl.metafactory.agentruntime.definition.skill.SkillPackageProvider.SkillLookup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefinitionRecordsTest {

    @Test
    void aResultCarriesADocumentExactlyWhenItIsFound() {
        assertThatThrownBy(() -> new ProviderResult(SourceStatus.FOUND, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SkillLookup(SourceStatus.FOUND, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringNeverContainsInstructionsOrResourceContent() {
        var doc = new AgentDefinitionDocument("a", "A", "1", "d", "m", null, null, null, null,
                "secret instructions", "hash", "raw secret");
        var skill = new SkillPackage("s", "d", "secret steps", Map.of("references/a.md", "secret resource"),
                List.of(), List.of(), "hash");

        assertThat(doc.toString()).contains("id=a").doesNotContain("secret");
        assertThat(skill.toString()).contains("references/a.md").doesNotContain("secret");
    }
}
