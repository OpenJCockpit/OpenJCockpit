package nl.metafactory.agents.definition.source;

import nl.metafactory.agents.definition.model.DefinitionSource;
import nl.metafactory.agents.definition.model.ProviderResult;

/** Fetches an agent definition from exactly one kind of source and reports an explicit status. */
public interface AgentDefinitionProvider {

    DefinitionSource source();

    ProviderResult lookup(String agentId);
}
