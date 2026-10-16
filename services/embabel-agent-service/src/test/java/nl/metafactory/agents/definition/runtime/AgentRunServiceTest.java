package nl.metafactory.agents.definition.runtime;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.common.ai.prompt.PromptContributor;
import nl.metafactory.agents.definition.binding.AgentTenantBinding;
import nl.metafactory.agents.definition.binding.AgentTenantBindingRepository;
import nl.metafactory.agents.definition.guardrail.GuardrailPolicyEnforcer;
import nl.metafactory.agents.definition.guardrail.PiiProtectionGuardrail;
import nl.metafactory.agents.definition.markdown.AgentMarkdownParser;
import nl.metafactory.agents.definition.model.DefinitionSource;
import nl.metafactory.agents.definition.model.ProviderResult;
import nl.metafactory.agents.definition.resolver.AgentDefinitionResolver;
import nl.metafactory.agents.definition.resolver.FallbackPolicy;
import nl.metafactory.agents.definition.resolver.ModelAliasCatalog;
import nl.metafactory.agents.definition.resolver.SourceChainSelector;
import nl.metafactory.agents.definition.skill.SkillResolver;
import nl.metafactory.agents.definition.source.AgentDefinitionProvider;
import nl.metafactory.agents.definition.tool.ToolAccessPolicy;
import nl.metafactory.agents.definition.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.slf4j.MDC;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRunServiceTest {

    /** A "registry" whose content changes on every lookup, like a remote source edited mid-run. */
    static final class MutableProvider implements AgentDefinitionProvider {
        final AtomicInteger lookups = new AtomicInteger();

        public DefinitionSource source() {
            return DefinitionSource.LOCAL;
        }

        public ProviderResult lookup(String agentId) {
            int n = lookups.incrementAndGet();
            return ProviderResult.found(new AgentMarkdownParser().parse(
                    "---\nid: helper\nversion: \"" + n + ".0.0\"\nmodel: best\n---\nInstructions revision " + n + "\n"));
        }
    }

    final List<String> contributions = new ArrayList<>();
    final List<String> mdcDuringRun = new ArrayList<>();
    final MutableProvider provider = new MutableProvider();

    AgentRunService service() {
        var repo = mock(AgentTenantBindingRepository.class);
        when(repo.find("tenant-a", "helper")).thenReturn(Optional.of(
                new AgentTenantBinding("tenant-a", "helper", true, null, null, false, false, Set.of(), Set.of())));
        var resolver = new AgentDefinitionResolver(repo, new SourceChainSelector(List.of(provider), FallbackPolicy.NONE),
                new SkillResolver(List.of()), new ToolAccessPolicy(new ToolRegistry(List.of())),
                new GuardrailPolicyEnforcer(List.of(new PiiProtectionGuardrail()), Set.of()),
                new ModelAliasCatalog(Set.of("best")), Clock.systemUTC());

        var ai = mock(Ai.class);
        var runner = mock(PromptRunner.class, Answers.RETURNS_SELF);
        when(ai.withLlmByRole("best")).thenReturn(runner);
        when(runner.withPromptContributor(any())).thenAnswer(inv -> {
            // Embabel may render contributors repeatedly (e.g. per tool-loop iteration).
            PromptContributor c = inv.getArgument(0);
            contributions.add(c.contribution());
            provider.lookup("helper"); // the registry changes underneath the running agent
            contributions.add(c.contribution());
            return runner;
        });
        when(runner.generateText(anyString())).thenAnswer(inv -> {
            mdcDuringRun.add(MDC.get("traceId") + "|" + MDC.get("tenantId") + "|" + MDC.get("agentId") + "|"
                    + MDC.get("agentVersion"));
            return "done";
        });
        return new AgentRunService(resolver, new ConfiguredDefinitionAgent(ai, (r, t) -> ToolCallAuthorizer.Decision.allow()));
    }

    @Test
    void runSeesOneStableSnapshotEvenWhenTheSourceChanges() {
        var response = service().run("tenant-a", "helper", "alice", null, "hi");

        assertThat(response.version()).isEqualTo("1.0.0");
        assertThat(contributions).hasSize(2).allMatch(c -> c.contains("Instructions revision 1"));
        assertThat(provider.lookups).hasValue(2); // one resolution + the simulated external edit, no re-fetch
    }

    @Test
    void traceparentIsPropagatedAndMdcIsScopedToTheRun() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        var response = service().run("tenant-a", "helper", "alice", "00-" + traceId + "-00f067aa0ba902b7-01", "hi");

        assertThat(response.traceId()).isEqualTo(traceId);
        assertThat(mdcDuringRun).containsExactly(traceId + "|tenant-a|helper|1.0.0");
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("tenantId")).isNull();
    }

    @Test
    void invalidTraceparentGetsAFreshTraceId() {
        var response = service().run("tenant-a", "helper", "alice", "garbage\r\ninjected", "hi");
        assertThat(response.traceId()).matches("[0-9a-f]{32}");
    }

    @Test
    void runContextRejectsLogInjection() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RunContext("r", "t\nfake=1", "a", "b", "1", "h", "p"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
