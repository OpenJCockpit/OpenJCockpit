package nl.metafactory.agentruntime.definition.runtime;

import nl.metafactory.agentruntime.definition.binding.AgentTenantBindings;
import nl.metafactory.agentruntime.definition.guardrail.GuardrailPolicyEnforcer;
import nl.metafactory.agentruntime.definition.model.AgentResolutionException;
import nl.metafactory.agentruntime.definition.model.ResolutionFailure;
import nl.metafactory.agentruntime.definition.resolver.ModelAliasCatalog;
import nl.metafactory.agentruntime.definition.skill.SkillPackageProvider;
import nl.metafactory.agentruntime.definition.tool.ToolRegistry;
import nl.metafactory.common.agentdefinition.AgentMarkdownParser;
import nl.metafactory.common.agentdefinition.SourceStatus;
import nl.metafactory.common.agentruntime.AgentExecutionRequest.DefinitionSnapshot;
import nl.metafactory.common.agentruntime.AgentTenantBinding;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A catalog agent is resolved from its snapshot with the same tenant binding checks as a served agent. */
class CatalogAgentRunsTest {

    private static final String MARKDOWN = "---\nid: triage\nversion: \"2\"\nmodel: agent-chat\n---\n# Role\nTriage.\n";

    private final AgentTenantBindings bindings = mock(AgentTenantBindings.class);
    private final ConfiguredDefinitionAgent agent = mock(ConfiguredDefinitionAgent.class);
    private final CatalogAgentRuns runs = new CatalogAgentRuns(bindings, new AgentMarkdownParser(),
            mock(SkillPackageProvider.class), new ToolRegistry(List.of()), new GuardrailPolicyEnforcer(List.of(), Set.of()),
            new ModelAliasCatalog(Set.of("agent-chat")), agent);

    @Test
    void theSnapshotIsTheDefinitionOfTheRun() {
        when(bindings.find("local", "triage")).thenReturn(Optional.of(
                new AgentTenantBinding("local", "triage", true, null, null, false, false, Set.of(), Set.of())));
        when(agent.respond(any())).thenReturn(new ConfiguredDefinitionAgent.Response("triage", "2", "h", "t", "done"));

        var response = runs.forSnapshot(DefinitionSnapshot.found(MARKDOWN)).run("local", "triage", "alice", null, "hi", null);

        assertThat(response.text()).isEqualTo("done");
        var request = ArgumentCaptor.forClass(ConfiguredDefinitionAgent.Request.class);
        verify(agent).respond(request.capture());
        assertThat(request.getValue().agent().definition().version()).isEqualTo("2");
    }

    @Test
    void theBindingIsCheckedFirstAndTheCatalogsFailureIsNotFallenBackFrom() {
        when(bindings.find("local", "triage")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> runs.forSnapshot(DefinitionSnapshot.found(MARKDOWN)).run("local", "triage", "a", null, "m", null))
                .isInstanceOfSatisfying(AgentResolutionException.class,
                        e -> assertThat(e.failure()).isEqualTo(ResolutionFailure.TENANT_FORBIDDEN));

        when(bindings.find("local", "triage")).thenReturn(Optional.of(
                new AgentTenantBinding("local", "triage", true, null, null, true, true, Set.of(), Set.of())));
        assertThatThrownBy(() -> runs.forSnapshot(DefinitionSnapshot.of(SourceStatus.NOT_FOUND, "gone"))
                .run("local", "triage", "a", null, "m", null))
                .isInstanceOfSatisfying(AgentResolutionException.class,
                        e -> assertThat(e.failure()).isEqualTo(ResolutionFailure.NOT_FOUND));
    }
}
