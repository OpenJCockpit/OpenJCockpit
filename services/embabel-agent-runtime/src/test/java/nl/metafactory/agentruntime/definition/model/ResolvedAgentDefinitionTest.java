package nl.metafactory.agentruntime.definition.model;

import nl.metafactory.common.agentdefinition.SourceStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResolvedAgentDefinitionTest {

    static final String HASH = "a".repeat(64);

    static ResolvedAgentDefinition build(List<String> tools, List<String> guardrails, Map<String, String> executors) {
        return new ResolvedAgentDefinition("customer-support", "tenant-a", "# Role\nHelp.", "customer-chat",
                List.of("customer-lookup"), tools, guardrails, DefinitionSource.LOCAL, "1.0.0", HASH,
                Instant.parse("2026-10-14T10:00:00Z"), executors,
                List.of(new SourceAttempt(DefinitionSource.LOCAL, SourceStatus.FOUND, null)));
    }

    @Test
    void constructsAValidSnapshot() {
        var def = build(List.of("customer.read"), List.of("pii-protection"), Map.of("pii-protection", "opa"));
        assertThat(def.agentId()).isEqualTo("customer-support");
        assertThat(def.guardrailExecutors()).containsEntry("pii-protection", "opa");
    }

    @Test
    void collectionsAreDefensivelyCopiedAndUnmodifiable() {
        var tools = new ArrayList<>(List.of("customer.read"));
        var executors = new HashMap<String, String>();
        var def = build(tools, List.of(), executors);
        tools.add("customer.delete");
        executors.put("x", "y");

        assertThat(def.toolIds()).containsExactly("customer.read");
        assertThat(def.guardrailExecutors()).isEmpty();
        assertThatThrownBy(() -> def.toolIds().add("customer.delete"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> def.skillIds().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsGuardrailWithoutExecutor() {
        assertThatThrownBy(() -> build(List.of(), List.of("pii-protection"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pii-protection");
    }

    @Test
    void rejectsBlankIdentityAndBadHash() {
        assertThatThrownBy(() -> new ResolvedAgentDefinition(" ", "t", "i", "m", null, null, null,
                DefinitionSource.LOCAL, "1", HASH, Instant.now(), null, null))
                .hasMessageContaining("agentId");
        assertThatThrownBy(() -> new ResolvedAgentDefinition("a", "t", "i", "m", null, null, null,
                DefinitionSource.LOCAL, "1", "not-a-hash", Instant.now(), null, null))
                .hasMessageContaining("contentHash");
    }

    @Test
    void toStringNeverContainsInstructions() {
        var def = build(List.of(), List.of(), Map.of());
        assertThat(def.toString()).doesNotContain("Help.").contains("customer-support");
    }

    @Test
    void onlyNotFoundUnavailableAndUnsupportedAreFallbackCandidates() {
        assertThat(SourceStatus.UNAUTHORIZED.isFallbackCandidate()).isFalse();
        assertThat(SourceStatus.INVALID.isFallbackCandidate()).isFalse();
        assertThat(SourceStatus.FOUND.isFallbackCandidate()).isFalse();
        assertThat(SourceStatus.NOT_FOUND.isFallbackCandidate()).isTrue();
        assertThat(SourceStatus.UNAVAILABLE.isFallbackCandidate()).isTrue();
    }
}
