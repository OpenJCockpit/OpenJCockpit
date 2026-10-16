package nl.metafactory.aicontrol.client;

import java.util.List;

/** Agent catalog from embabel-agent-service: local /agents folder plus A2A providers. */
public record AgentCatalogDto(
        AgentCatalogLocalSectionDto local,
        List<AgentCatalogProviderSectionDto> providers
) {
}
