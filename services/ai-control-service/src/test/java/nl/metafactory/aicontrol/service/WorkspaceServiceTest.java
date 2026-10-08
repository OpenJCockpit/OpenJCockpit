package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.client.AgentEventDto;
import nl.metafactory.aicontrol.client.AgentRunDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.model.SpecFile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceServiceTest {

    private final SpecRepositoryClient specRepo = mock(SpecRepositoryClient.class);
    private final EmbabelAgentClient agentClient = mock(EmbabelAgentClient.class);
    private final WorkspaceService service = new WorkspaceService(specRepo, agentClient);

    @Test
    void loadWorkspaceWithNoRunUsesDefaults() {
        when(specRepo.listSpecs(any())).thenReturn(List.of(
            new SpecFile("s1", "test.spec.md", "owner", "now", "Active", true, "", "")
        ));
        when(agentClient.getLatestRun(any())).thenReturn(Optional.empty());

        var workspace = service.loadWorkspace("noordzee-logistics");

        assertThat(workspace.agents()).hasSize(6);
        assertThat(workspace.agents().get(0).statusKind()).isEqualTo("active");
        assertThat(workspace.customer().id()).isEqualTo("noordzee-logistics");
    }

    @Test
    void loadWorkspaceWithRunMapsLiveStatuses() {
        when(specRepo.listSpecs(any())).thenReturn(List.of());
        var events = List.of(
            new AgentEventDto(Instant.now(), "requirement", "Spec loaded", "OK", "evidence://req"),
            new AgentEventDto(Instant.now(), "impact", "Impact recorded", "RUNNING", "evidence://impact")
        );
        var run = new AgentRunDto("run-1", "cust", "spec.md", "", "RUNNING", Instant.now(), events, List.of(), null, null, null, null);
        when(agentClient.getLatestRun(any())).thenReturn(Optional.of(run));

        var workspace = service.loadWorkspace("test-customer");

        var agents = workspace.agents();
        assertThat(agents.stream().filter(a -> "requirement".equals(a.id())).findFirst())
            .hasValueSatisfying(a -> assertThat(a.statusKind()).isEqualTo("done"));
        assertThat(agents.stream().filter(a -> "impact".equals(a.id())).findFirst())
            .hasValueSatisfying(a -> assertThat(a.statusKind()).isEqualTo("active"));
        assertThat(agents.stream().filter(a -> "test-design".equals(a.id())).findFirst())
            .hasValueSatisfying(a -> assertThat(a.statusKind()).isEqualTo("waiting"));
    }

    @Test
    void mapToAgentCardsWithUnknownStatusFallsBackToWaiting() {
        var events = List.of(
            new AgentEventDto(Instant.now(), "requirement", "title", "UNKNOWN_STATUS", "ref")
        );
        var run = new AgentRunDto("r", "c", "s", "", "RUNNING", Instant.now(), events, List.of(), null, null, null, null);
        var cards = service.mapToAgentCards(run);
        assertThat(cards.stream().filter(c -> "requirement".equals(c.id())).findFirst())
            .hasValueSatisfying(c -> assertThat(c.statusKind()).isEqualTo("waiting"));
    }
}
