package nl.metafactory.agents.definition.resolver;

import nl.metafactory.agents.definition.binding.AgentTenantBinding;
import nl.metafactory.agents.definition.cache.LastKnownGoodStore;
import nl.metafactory.agents.definition.config.AgentDefinitionProperties;
import nl.metafactory.agents.definition.guardrail.GuardrailPolicyEnforcer;
import nl.metafactory.agents.definition.markdown.AgentMarkdownParser;
import nl.metafactory.agents.definition.model.AgentResolutionException;
import nl.metafactory.agents.definition.model.DefinitionSource;
import nl.metafactory.agents.definition.model.ResolutionFailure;
import nl.metafactory.agents.definition.model.SourceStatus;
import nl.metafactory.agents.definition.skill.LocalSkillInventory;
import nl.metafactory.agents.definition.skill.SkillMarkdownParser;
import nl.metafactory.agents.definition.skill.SkillResolver;
import nl.metafactory.agents.definition.source.ConfinedRoot;
import nl.metafactory.agents.definition.tool.ToolAccessPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static nl.metafactory.agents.definition.resolver.ExternalPrecedenceTest.REMOTE_DOC;
import static nl.metafactory.agents.definition.resolver.ExternalPrecedenceTest.remote;
import static nl.metafactory.agents.definition.resolver.ResolverFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FallbackPolicyMatrixTest {

    @TempDir
    Path lkgDir;

    static AgentDefinitionProperties.Fallback config(boolean onUnavailable, boolean onNotFound) {
        var f = new AgentDefinitionProperties.Fallback();
        f.setOnUnavailable(onUnavailable);
        f.setOnNotFound(onNotFound);
        return f;
    }

    static AgentTenantBinding binding(boolean fallbackAllowed, boolean fallbackOnNotFound) {
        return new AgentTenantBinding("tenant-a", "customer-support", true, null, null, fallbackAllowed,
                fallbackOnNotFound, Set.of("customer.read"), Set.of());
    }

    LastKnownGoodStore lkg() {
        return new LastKnownGoodStore(lkgDir, Duration.ofHours(1), 10, CLOCK, new AgentMarkdownParser());
    }

    AgentDefinitionResolver resolver(SourceStatus remoteStatus, AgentTenantBinding binding,
                                     AgentDefinitionProperties.Fallback cfg, LastKnownGoodStore lkg) {
        var selector = new SourceChainSelector(List.of(remote(remoteStatus, REMOTE_DOC), lkg, localProvider()),
                new ConfiguredFallbackPolicy(cfg));
        return new AgentDefinitionResolver(repo(binding), selector,
                new SkillResolver(List.of(new LocalSkillInventory(new ConfinedRoot(ROOT), new SkillMarkdownParser()))),
                new ToolAccessPolicy(tools()),
                new GuardrailPolicyEnforcer(List.of(new nl.metafactory.agents.definition.guardrail.PiiProtectionGuardrail()), Set.of()),
                new ModelAliasCatalog(Set.of("customer-chat")), CLOCK, lkg);
    }

    /**
     * remote status | binding fallback | binding on-not-found | cfg on-unavailable | cfg on-not-found
     * → expected source or failure.
     */
    @ParameterizedTest(name = "{0} allowed={1}/{2} cfg={3}/{4} -> {5}")
    @CsvSource({
            "UNAVAILABLE,  true,  false, true,  false, LOCAL",
            "UNAVAILABLE,  false, false, true,  false, UNAVAILABLE",
            "UNAVAILABLE,  true,  false, false, false, UNAVAILABLE",
            "NOT_FOUND,    true,  true,  true,  true,  LOCAL",
            "NOT_FOUND,    true,  false, true,  true,  NOT_FOUND",
            "NOT_FOUND,    true,  true,  true,  false, NOT_FOUND",
            "UNAUTHORIZED, true,  true,  true,  true,  UNAUTHORIZED",
            "INVALID,      true,  true,  true,  true,  INVALID",
            "FOUND,        true,  true,  true,  true,  REMOTE_LITELLM",
    })
    void matrix(SourceStatus remoteStatus, boolean allowed, boolean bindingOnNotFound, boolean cfgUnavailable,
                boolean cfgNotFound, String expected) {
        var r = resolver(remoteStatus, binding(allowed, bindingOnNotFound), config(cfgUnavailable, cfgNotFound), lkg());
        if (expected.equals("LOCAL") || expected.equals("REMOTE_LITELLM")) {
            assertThat(r.resolve("tenant-a", "customer-support").definition().source())
                    .isEqualTo(DefinitionSource.valueOf(expected));
        } else {
            assertThatThrownBy(() -> r.resolve("tenant-a", "customer-support"))
                    .isInstanceOf(AgentResolutionException.class)
                    .extracting(e -> ((AgentResolutionException) e).failure())
                    .isEqualTo(ResolutionFailure.valueOf(expected));
        }
    }

    @Test
    void remoteOutageUsesLastKnownGoodBeforeLocal() {
        var store = lkg();
        resolver(SourceStatus.FOUND, binding(true, false), config(true, false), store).resolve("tenant-a", "customer-support");

        var d = resolver(SourceStatus.UNAVAILABLE, binding(true, false), config(true, false), store)
                .resolve("tenant-a", "customer-support").definition();
        assertThat(d.source()).isEqualTo(DefinitionSource.LAST_KNOWN_GOOD);
        assertThat(d.version()).isEqualTo("2.0.0");
        assertThat(d.attempts()).extracting(a -> a.source() + ":" + a.status())
                .containsExactly("REMOTE_LITELLM:UNAVAILABLE", "LAST_KNOWN_GOOD:FOUND");
    }

    @Test
    void staleCacheFallsThroughToLocalOnlyWhenAllowed() throws Exception {
        var now = new java.util.concurrent.atomic.AtomicReference<>(CLOCK.instant());
        var clock = new java.time.Clock() {
            public java.time.ZoneId getZone() {
                return java.time.ZoneOffset.UTC;
            }

            public java.time.Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            public java.time.Instant instant() {
                return now.get();
            }
        };
        var store = new LastKnownGoodStore(lkgDir, Duration.ofMinutes(5), 10, clock, new AgentMarkdownParser());
        resolver(SourceStatus.FOUND, binding(true, false), config(true, false), store).resolve("tenant-a", "customer-support");
        now.set(now.get().plus(Duration.ofMinutes(6)));
        assertThatThrownBy(() -> resolver(SourceStatus.UNAVAILABLE, binding(false, false), config(true, false), store)
                .resolve("tenant-a", "customer-support"))
                .extracting(e -> ((AgentResolutionException) e).failure()).isEqualTo(ResolutionFailure.UNAVAILABLE);
        resolver(SourceStatus.FOUND, binding(true, false), config(true, false), store).resolve("tenant-a", "customer-support");
        now.set(now.get().plus(Duration.ofMinutes(6)));
        assertThat(resolver(SourceStatus.UNAVAILABLE, binding(true, false), config(true, false), store)
                .resolve("tenant-a", "customer-support").definition().source()).isEqualTo(DefinitionSource.LOCAL);
        assertThat(Files.list(lkgDir).count()).isZero();
    }

    @Test
    void cachedSnapshotCannotBypassRevokedToolRights() {
        var store = lkg();
        resolver(SourceStatus.FOUND, binding(true, false), config(true, false), store).resolve("tenant-a", "customer-support");
        var revoked = new AgentTenantBinding("tenant-a", "customer-support", true, null, null, true, false, Set.of(), Set.of());
        assertThatThrownBy(() -> resolver(SourceStatus.UNAVAILABLE, revoked, config(true, false), store)
                .resolve("tenant-a", "customer-support"))
                .extracting(e -> ((AgentResolutionException) e).failure()).isEqualTo(ResolutionFailure.TOOL_DENIED);
    }

    @Test
    void fallbackDefinitionCannotDropAMandatoryGuardrail() {
        var withMissingExecutor = new AgentTenantBinding("tenant-a", "customer-support", true, null, null, true, false,
                Set.of("customer.read"), Set.of("toxicity"));
        assertThatThrownBy(() -> resolver(SourceStatus.UNAVAILABLE, withMissingExecutor, config(true, false), lkg())
                .resolve("tenant-a", "customer-support"))
                .extracting(e -> ((AgentResolutionException) e).failure()).isEqualTo(ResolutionFailure.GUARDRAIL_MISSING);
    }
}
