package nl.metafactory.agentruntime.definition.binding;

import nl.metafactory.agentruntime.definition.model.AgentResolutionException;
import nl.metafactory.agentruntime.definition.model.ResolutionFailure;
import nl.metafactory.agentruntime.management.AgentServiceClient;
import nl.metafactory.common.agentruntime.AgentTenantBinding;

import java.util.Optional;

/**
 * Tenant bindings read from the agent service. When it cannot answer, resolution fails as
 * {@link ResolutionFailure#UNAVAILABLE} (transient): no binding is ever assumed.
 */
public class RemoteAgentTenantBindings implements AgentTenantBindings {

    private final AgentServiceClient agentService;

    public RemoteAgentTenantBindings(AgentServiceClient agentService) {
        this.agentService = agentService;
    }

    @Override
    public Optional<AgentTenantBinding> find(String tenantId, String agentId) {
        try {
            return agentService.tenantBinding(tenantId, agentId);
        } catch (AgentServiceClient.ManagementUnavailableException e) {
            throw new AgentResolutionException(ResolutionFailure.UNAVAILABLE, "tenant bindings are unavailable");
        }
    }
}
