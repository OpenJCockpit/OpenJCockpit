package nl.metafactory.agents.workflow.agents;

import nl.metafactory.agents.a2a.client.A2AProviderClient;
import nl.metafactory.agents.a2a.client.A2AProviderClient.Status;
import nl.metafactory.agents.a2a.client.A2AProviderClients;
import nl.metafactory.agents.catalog.AgentCatalog;
import nl.metafactory.agents.catalog.LocalAgentCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogAgentReferenceCheckTest {

    final LocalAgentCatalog local = mock(LocalAgentCatalog.class);
    final A2AProviderClients providers = mock(A2AProviderClients.class);
    final A2AProviderClient litellm = mock(A2AProviderClient.class);
    final CatalogAgentReferenceCheck check = new CatalogAgentReferenceCheck(local, providers);

    static AgentCatalog.LocalAgent localAgent(String id, boolean valid) {
        return new AgentCatalog.LocalAgent(id, id, null, "1", "m", id + ".md", List.of(), List.of(), List.of(), valid, null);
    }

    @Test
    void localReferenceMustBeAValidAgentInTheAgentsFolder() {
        when(local.list()).thenReturn(new AgentCatalog.LocalSection("Local", "OK", null,
                List.of(localAgent("triage", true), localAgent("broken", false))));
        assertThat(check.problem(AgentRef.local("triage"))).isEmpty();
        assertThat(check.problem(AgentRef.local("broken"))).isPresent();
        assertThat(check.problem(AgentRef.local("missing"))).get().asString().contains("/agents");
    }

    @Test
    void a2aReferenceMustBeListedByAConfiguredProvider() {
        when(providers.find("litellm")).thenReturn(Optional.of(litellm));
        when(litellm.name()).thenReturn("LiteLLM");
        when(litellm.listAgents()).thenReturn(new A2AProviderClient.Listing(Status.OK, List.of(new A2AProviderClient.Agent(
                "id", "qa-engineer", null, "u", "c", "b", "0.3.0", "1", false, List.of())), 0, null));

        assertThat(check.problem(AgentRef.a2a("litellm", "qa-engineer"))).isEmpty();
        assertThat(check.problem(AgentRef.a2a("litellm", "unknown"))).get().asString().contains("LiteLLM");
        assertThat(check.problem(AgentRef.a2a("other", "x"))).get().asString().contains("not configured");
    }

    @Test
    void unreachableProviderDoesNotBlockSaving() {
        when(providers.find("litellm")).thenReturn(Optional.of(litellm));
        when(litellm.listAgents()).thenReturn(new A2AProviderClient.Listing(Status.UNAVAILABLE, List.of(), 0, "down"));
        assertThat(check.problem(AgentRef.a2a("litellm", "qa-engineer"))).isEmpty();
    }
}
