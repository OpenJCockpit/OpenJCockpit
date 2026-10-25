package nl.metafactory.common.agentdefinition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefinitionFormatRecordsTest {

    @Test
    void onlySoftFailuresAreFallbackCandidates() {
        assertThat(SourceStatus.NOT_FOUND.isFallbackCandidate()).isTrue();
        assertThat(SourceStatus.UNAVAILABLE.isFallbackCandidate()).isTrue();
        assertThat(SourceStatus.UNSUPPORTED.isFallbackCandidate()).isTrue();
        assertThat(SourceStatus.FOUND.isFallbackCandidate()).isFalse();
        assertThat(SourceStatus.INVALID.isFallbackCandidate()).isFalse();
        assertThat(SourceStatus.UNAUTHORIZED.isFallbackCandidate()).isFalse();
    }

    @Test
    void aDocumentNeverPrintsItsInstructionsOrRawMarkdown() {
        var doc = new AgentDefinitionDocument("a", "A", "1", "d", "m", null, null, null, null,
                "secret instructions", "hash", "raw secret");
        assertThat(doc.toString()).contains("id=a", "version=1", "contentHash=hash").doesNotContain("secret");
        assertThat(doc.skills()).isEmpty();
        assertThat(doc.tools()).isEmpty();
    }
}
