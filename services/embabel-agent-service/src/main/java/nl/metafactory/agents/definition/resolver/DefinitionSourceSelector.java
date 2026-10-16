package nl.metafactory.agents.definition.resolver;

import nl.metafactory.agents.definition.binding.AgentTenantBinding;

/** Decides which source's document a run uses. Throws {@code AgentResolutionException} when none may be used. */
@FunctionalInterface
public interface DefinitionSourceSelector {

    DefinitionSelection select(AgentTenantBinding binding);
}
