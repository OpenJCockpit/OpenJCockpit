package nl.metafactory.agentruntime.definition.binding;

import nl.metafactory.agentruntime.definition.model.AgentResolutionException;
import nl.metafactory.agentruntime.definition.model.ResolutionFailure;
import nl.metafactory.agentruntime.management.AgentServiceClient;
import nl.metafactory.common.agentruntime.AgentTenantBinding;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemoteAgentTenantBindingsTest {

    @Test
    void bindingsComeFromTheAgentServiceAndAnOutageIsATransientResolutionFailure() {
        var agentService = mock(AgentServiceClient.class);
        var binding = new AgentTenantBinding("local", "qa", true, null, null, false, false, null, null);
        when(agentService.tenantBinding("local", "qa")).thenReturn(Optional.of(binding));
        when(agentService.tenantBinding("local", "none")).thenReturn(Optional.empty());
        when(agentService.tenantBinding("local", "down"))
                .thenThrow(new AgentServiceClient.ManagementUnavailableException("down", null));
        var bindings = new RemoteAgentTenantBindings(agentService);

        assertThat(bindings.find("local", "qa")).contains(binding);
        assertThat(bindings.find("local", "none")).isEmpty();
        assertThatThrownBy(() -> bindings.find("local", "down"))
                .isInstanceOfSatisfying(AgentResolutionException.class,
                        e -> assertThat(e.failure()).isEqualTo(ResolutionFailure.UNAVAILABLE));
    }
}
