package nl.metafactory.agentruntime.definition.resolver;

import nl.metafactory.common.agentruntime.AgentTenantBinding;

/** Decides which source's document a run uses. Throws {@code AgentResolutionException} when none may be used. */
@FunctionalInterface
public interface DefinitionSourceSelector {

    DefinitionSelection select(AgentTenantBinding binding);
}
