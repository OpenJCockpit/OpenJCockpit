package nl.metafactory.agents.catalog;

import nl.metafactory.agents.a2a.client.A2AProviderClient;
import nl.metafactory.agents.a2a.client.A2AProviderClient.Status;
import nl.metafactory.agents.a2a.client.A2AProviderClients;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCatalogControllerTest {

    final A2AProviderClient litellm = mock(A2AProviderClient.class);
    final A2AProviderClients providers = mock(A2AProviderClients.class);
    final LocalAgentCatalog local = mock(LocalAgentCatalog.class);
    final AgentCatalogController controller = new AgentCatalogController(local, providers);

    @Test
    void oneSectionPerProviderClient() {
        when(local.list()).thenReturn(new AgentCatalog.LocalSection("Local", "OK", null, List.of()));
        when(providers.all()).thenReturn(List.of(litellm));
        when(litellm.id()).thenReturn("litellm");
        when(litellm.name()).thenReturn("LiteLLM");
        when(litellm.listAgents()).thenReturn(new A2AProviderClient.Listing(Status.OK, List.of(new A2AProviderClient.Agent(
                "id1", "qa-engineer", "Tests", "http://localhost:4100/a2a/qa-engineer", "card", "backend", "0.3.0", "1.0.0",
                false, List.of("QA Engineer"))), 0, null));

        var catalog = controller.catalog();
        assertThat(catalog.providers()).singleElement().satisfies(section -> {
            assertThat(section.name()).isEqualTo("LiteLLM");
            assertThat(section.type()).isEqualTo("litellm");
            assertThat(section.agents()).extracting(AgentCatalog.ProviderAgent::url)
                    .containsExactly("http://localhost:4100/a2a/qa-engineer");
        });
    }

    @Test
    void providerFailuresBecomeSectionStatus() {
        when(litellm.name()).thenReturn("LiteLLM");
        when(litellm.id()).thenReturn("litellm");
        when(litellm.listAgents()).thenReturn(new A2AProviderClient.Listing(Status.NOT_CONFIGURED, List.of(), 0, "no key"));
        assertThat(AgentCatalogController.section(litellm).status()).isEqualTo("NOT_CONFIGURED");
    }

    @Test
    void returnsTheCardOfAKnownProviderAndMapsFailures() {
        var card = JsonMapper.builder().build().createObjectNode().put("name", "qa-engineer");
        when(providers.find("litellm")).thenReturn(Optional.of(litellm));
        when(litellm.agentCard("qa-engineer")).thenReturn(new A2AProviderClient.CardResult(Status.OK, card));
        when(litellm.agentCard("x")).thenReturn(new A2AProviderClient.CardResult(Status.NOT_FOUND, null));
        when(litellm.agentCard("y")).thenReturn(new A2AProviderClient.CardResult(Status.UNAUTHORIZED, null));
        when(providers.find("other")).thenReturn(Optional.empty());

        assertThat(controller.card("litellm", "qa-engineer").getBody()).isEqualTo(card);
        assertThatThrownBy(() -> controller.card("litellm", "x")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        assertThatThrownBy(() -> controller.card("litellm", "y")).hasMessageContaining("502");
        assertThatThrownBy(() -> controller.card("other", "x")).hasMessageContaining("404");
    }
}
