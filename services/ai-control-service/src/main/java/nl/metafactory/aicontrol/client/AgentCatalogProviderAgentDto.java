package nl.metafactory.aicontrol.client;

import java.util.List;

/** One agent registered at an A2A provider. */
public record AgentCatalogProviderAgentDto(
        String id,
        String name,
        String description,
        String url,
        String cardUrl,
        String backendUrl,
        String protocolVersion,
        String version,
        boolean streaming,
        List<String> skills
) {
}
