package nl.metafactory.agentruntime.definition.resolver;

import nl.metafactory.agentruntime.definition.config.AgentDefinitionProperties.SourceKind;
import nl.metafactory.common.agentdefinition.AgentMarkdownParser;
import nl.metafactory.agentruntime.definition.model.DefinitionSource;
import nl.metafactory.agentruntime.definition.model.ProviderResult;
import nl.metafactory.agentruntime.definition.model.ResolutionFailure;
import nl.metafactory.common.agentdefinition.SourceStatus;
import nl.metafactory.agentruntime.definition.source.AgentDefinitionProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static nl.metafactory.agentruntime.definition.resolver.AgentDefinitionResolverTest.assertFailure;
import static nl.metafactory.agentruntime.definition.resolver.ResolverFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class ExternalPrecedenceTest {

    static final String REMOTE_DOC = """
            ---
            id: customer-support
            version: "2.0.0"
            model: customer-chat
            skills: [customer-lookup]
            tools: [customer.read]
            guardrails: [pii-protection]
            ---
            Remote instructions v2.
            """;

    static AgentDefinitionProvider remote(SourceStatus status, String doc) {
        return new AgentDefinitionProvider() {
            public DefinitionSource source() {
                return DefinitionSource.REMOTE_LITELLM;
            }

            public ProviderResult lookup(String agentId) {
                return status == SourceStatus.FOUND ? ProviderResult.found(new AgentMarkdownParser().parse(doc))
                        : ProviderResult.of(status, "stub");
            }
        };
    }

    static AgentDefinitionResolver resolverWith(AgentDefinitionProvider remote) {
        var ordered = SourceOrder.order(Map.of(SourceKind.REMOTE, remote, SourceKind.LOCAL, localProvider()),
                List.of(SourceKind.REMOTE, SourceKind.LAST_KNOWN_GOOD, SourceKind.LOCAL));
        return resolver(repo(binding("tenant-a", Set.of("customer.read"), Set.of())), ordered, FallbackPolicy.NONE,
                Set.of(), Set.of("customer-chat"));
    }

    @Test
    void validRemoteDefinitionWins() {
        var d = resolverWith(remote(SourceStatus.FOUND, REMOTE_DOC)).resolve("tenant-a", "customer-support").definition();
        assertThat(d.source()).isEqualTo(DefinitionSource.REMOTE_LITELLM);
        assertThat(d.version()).isEqualTo("2.0.0");
        assertThat(d.instructions()).isEqualTo("Remote instructions v2.");
    }

    @Test
    void remoteDefinitionGetsTheSameValidation() {
        String widened = REMOTE_DOC.replace("tools: [customer.read]", "tools: [customer.read, customer.delete]");
        assertFailure(() -> resolverWith(remote(SourceStatus.FOUND, widened)).resolve("tenant-a", "customer-support"),
                ResolutionFailure.TOOL_DENIED);
        String badModel = REMOTE_DOC.replace("model: customer-chat", "model: gpt-unknown");
        assertFailure(() -> resolverWith(remote(SourceStatus.FOUND, badModel)).resolve("tenant-a", "customer-support"),
                ResolutionFailure.INVALID);
    }

    @ParameterizedTest
    @CsvSource({"UNAUTHORIZED,UNAUTHORIZED", "INVALID,INVALID", "UNAVAILABLE,UNAVAILABLE", "NOT_FOUND,NOT_FOUND"})
    void remoteFailuresAreClassifiedAndDoNotSilentlyFallBack(SourceStatus remoteStatus, ResolutionFailure expected) {
        assertFailure(() -> resolverWith(remote(remoteStatus, null)).resolve("tenant-a", "customer-support"), expected);
    }

    @Test
    void localFirstPriorityIsHonoured() {
        var ordered = SourceOrder.order(Map.of(SourceKind.REMOTE, remote(SourceStatus.FOUND, REMOTE_DOC),
                SourceKind.LOCAL, localProvider()), List.of(SourceKind.LOCAL, SourceKind.REMOTE));
        assertThat(ordered).extracting(AgentDefinitionProvider::source)
                .containsExactly(DefinitionSource.LOCAL, DefinitionSource.REMOTE_LITELLM);
    }
}
