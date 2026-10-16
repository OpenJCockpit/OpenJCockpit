package nl.metafactory.aicontrol.client;

import java.util.List;

/** Agents of one A2A provider, e.g. LiteLLM. */
public record AgentCatalogProviderSectionDto(
        String name,
        String type,
        String status,
        String message,
        List<AgentCatalogProviderAgentDto> agents
) {
}
