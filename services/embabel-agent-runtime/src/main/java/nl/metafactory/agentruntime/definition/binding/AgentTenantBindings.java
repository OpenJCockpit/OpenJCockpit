package nl.metafactory.agentruntime.definition.binding;

import nl.metafactory.common.agentruntime.AgentTenantBinding;

import java.util.Optional;

/**
 * Server-side, tenant-scoped grants of agents. Owned and persisted by the agent service
 * ({@code agent_tenant_bindings}); the runtime only reads them. Every lookup is scoped by tenant and agent.
 */
@FunctionalInterface
public interface AgentTenantBindings {

    /** Empty when the tenant has no binding for the agent. */
    Optional<AgentTenantBinding> find(String tenantId, String agentId);
}
