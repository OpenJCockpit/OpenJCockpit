package nl.metafactory.aicontrol.client;

import java.util.List;

/** One local agent; {@code folders} is its hierarchy below /agents. */
public record AgentCatalogLocalAgentDto(
        String id,
        String name,
        String description,
        String version,
        String model,
        String path,
        List<String> folders,
        List<String> skills,
        List<String> tools,
        boolean valid,
        String problem
) {
}
