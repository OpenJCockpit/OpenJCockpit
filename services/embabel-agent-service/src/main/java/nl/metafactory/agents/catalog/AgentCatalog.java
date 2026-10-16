package nl.metafactory.agents.catalog;

import java.util.List;

/**
 * Wire model of {@code GET /api/agent-catalog}. {@code status} is one of {@code OK},
 * {@code NOT_CONFIGURED}, {@code UNAVAILABLE}, {@code UNAUTHORIZED}, {@code INVALID}.
 */
public record AgentCatalog(LocalSection local, List<ProviderSection> providers) {

    public record LocalSection(String name, String status, String message, List<LocalAgent> agents) {
    }

    /** {@code folders} is the path below {@code /agents}, used by the dashboard as hierarchy. */
    public record LocalAgent(String id, String name, String description, String version, String model,
                             String path, List<String> folders, List<String> skills, List<String> tools,
                             boolean valid, String problem) {
    }

    public record ProviderSection(String name, String type, String status, String message, List<ProviderAgent> agents) {
    }

    /**
     * {@code url} is the provider's gateway endpoint as reachable by users, {@code cardUrl} its hosted
     * agent card; {@code backendUrl} is where the provider routes to (may be network-internal).
     */
    public record ProviderAgent(String id, String name, String description, String url, String cardUrl,
                                String backendUrl, String protocolVersion, String version, boolean streaming,
                                List<String> skills) {
    }
}
