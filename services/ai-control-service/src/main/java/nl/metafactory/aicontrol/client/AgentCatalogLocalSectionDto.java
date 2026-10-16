package nl.metafactory.aicontrol.client;

import java.util.List;

/** Agents from the repository /agents folder. */
public record AgentCatalogLocalSectionDto(
        String name,
        String status,
        String message,
        List<AgentCatalogLocalAgentDto> agents
) {
}
