package nl.metafactory.agentruntime.definition.skill;

import nl.metafactory.common.agentdefinition.AgentDefinitionDocument;
import nl.metafactory.agentruntime.definition.model.AgentResolutionException;
import nl.metafactory.agentruntime.definition.model.DefinitionSource;
import nl.metafactory.agentruntime.definition.model.ResolutionFailure;
import nl.metafactory.common.agentdefinition.SourceStatus;
import nl.metafactory.common.agentdefinition.ConfinedRoot;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillResolverTest {

    private final LocalSkillInventory local = new LocalSkillInventory(
            new ConfinedRoot(Path.of("src/test/resources/agent-definitions")), new SkillMarkdownParser());

    static AgentDefinitionDocument agent(List<String> required, List<String> optional) {
        return new AgentDefinitionDocument("a", null, "1", null, "m", required, optional, List.of(), List.of(), "x",
                "0".repeat(64), null);
    }

    static SkillPackageProvider fixed(Map<String, SourceStatus> statuses) {
        return new SkillPackageProvider() {
            public DefinitionSource source() {
                return DefinitionSource.REMOTE_LITELLM;
            }

            public SkillLookup lookup(String id) {
                return SkillLookup.of(statuses.getOrDefault(id, SourceStatus.NOT_FOUND), "stub");
            }
        };
    }

    @Test
    void requiredSkillResolves() {
        var resolved = new SkillResolver(List.of(local)).resolve(agent(List.of("customer-lookup"), List.of()));
        assertThat(resolved.skills()).extracting(SkillPackage::id).containsExactly("customer-lookup");
    }

    @Test
    void missingRequiredSkillBlocks() {
        assertThatThrownBy(() -> new SkillResolver(List.of(local)).resolve(agent(List.of("nope"), List.of())))
                .isInstanceOf(AgentResolutionException.class)
                .extracting(e -> ((AgentResolutionException) e).failure()).isEqualTo(ResolutionFailure.SKILL_MISSING);
    }

    @Test
    void missingOptionalSkillIsOmittedAndReported() {
        var resolved = new SkillResolver(List.of(local))
                .resolve(agent(List.of("customer-lookup"), List.of("nice-to-have")));
        assertThat(resolved.skills()).hasSize(1);
        assertThat(resolved.omittedOptional()).containsExactly("nice-to-have");
    }

    @Test
    void anAvailableOptionalSkillIsLoaded() {
        var resolved = new SkillResolver(List.of(local)).resolve(agent(List.of(), List.of("customer-lookup")));

        assertThat(resolved.skills()).extracting(SkillPackage::id).containsExactly("customer-lookup");
        assertThat(resolved.omittedOptional()).isEmpty();
    }

    @Test
    void invalidOptionalSkillStillBlocks() {
        var resolver = new SkillResolver(List.of(fixed(Map.of("broken", SourceStatus.INVALID)), local));
        assertThatThrownBy(() -> resolver.resolve(agent(List.of(), List.of("broken"))))
                .extracting(e -> ((AgentResolutionException) e).failure()).isEqualTo(ResolutionFailure.INVALID);
    }

    @Test
    void unauthorizedRemoteSkillDoesNotFallBackToLocal() {
        var resolver = new SkillResolver(List.of(fixed(Map.of("customer-lookup", SourceStatus.UNAUTHORIZED)), local));
        assertThatThrownBy(() -> resolver.resolve(agent(List.of("customer-lookup"), List.of())))
                .extracting(e -> ((AgentResolutionException) e).failure()).isEqualTo(ResolutionFailure.UNAUTHORIZED);
    }

    @Test
    void unsupportedRemoteFallsThroughToLocal() {
        var resolver = new SkillResolver(List.of(fixed(Map.of("customer-lookup", SourceStatus.UNSUPPORTED)), local));
        assertThat(resolver.resolve(agent(List.of("customer-lookup"), List.of())).skills()).hasSize(1);
    }

    @Test
    void unavailableOptionalSkillBlocksInsteadOfBeingSilentlyDropped() {
        var resolver = new SkillResolver(List.of(fixed(Map.of("flaky", SourceStatus.UNAVAILABLE))));
        assertThatThrownBy(() -> resolver.resolve(agent(List.of(), List.of("flaky"))))
                .isInstanceOf(AgentResolutionException.class);
    }
}
