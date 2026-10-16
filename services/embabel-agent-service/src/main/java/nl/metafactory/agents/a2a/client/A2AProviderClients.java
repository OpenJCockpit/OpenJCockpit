package nl.metafactory.agents.a2a.client;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Creates exactly one {@link A2AProviderClient} per provider and hands out those instances; nothing
 * else constructs provider HTTP clients. Providers come from configuration (fixed at startup; unknown
 * types or duplicates fail startup) and from the dashboard (A2A services stored in the database,
 * replaced as a whole by {@link #replaceAdded(List)} whenever they change).
 */
@Component
public class A2AProviderClients {

    private final List<A2AProviderClient> configured;
    private volatile List<A2AProviderClient> added = List.of();

    public A2AProviderClients(A2AProvidersProperties properties) {
        this.configured = create(properties.getA2aProviders(), Set.of());
    }

    public List<A2AProviderClient> all() {
        return Stream.concat(configured.stream(), added.stream()).toList();
    }

    public Optional<A2AProviderClient> find(String id) {
        return all().stream().filter(c -> c.id().equals(id)).findFirst();
    }

    /** True when {@code id} is a provider from configuration (read-only in the dashboard). */
    public boolean isConfigured(String id) {
        return configured.stream().anyMatch(c -> c.id().equals(id));
    }

    /** Replaces the providers added in the dashboard; their ids may not collide with configured ones. */
    public synchronized void replaceAdded(List<A2AProvidersProperties.Provider> providers) {
        var reserved = new HashSet<String>();
        configured.forEach(c -> reserved.add(c.id()));
        this.added = create(providers, reserved);
    }

    /**
     * The LiteLLM client from configuration, if any (for LiteLLM-specific features such as policy checks
     * and guardrails, wired at startup). Providers added in the dashboard never take this role.
     */
    public Optional<LiteLlmA2AProviderClient> litellm() {
        return configured.stream().filter(LiteLlmA2AProviderClient.class::isInstance)
                .map(LiteLlmA2AProviderClient.class::cast).filter(LiteLlmA2AProviderClient::configured).findFirst();
    }

    private static List<A2AProviderClient> create(List<A2AProvidersProperties.Provider> providers, Set<String> reserved) {
        var created = new ArrayList<A2AProviderClient>();
        var ids = new HashSet<>(reserved);
        for (var provider : providers) {
            A2AProviderClient client = switch (provider.getType() == null ? "" : provider.getType()) {
                case LiteLlmA2AProviderClient.TYPE -> new LiteLlmA2AProviderClient(provider);
                default -> throw new IllegalStateException("Unsupported A2A provider type: " + provider.getType());
            };
            if (!ids.add(client.id())) {
                throw new IllegalStateException("A2A provider configured twice: " + client.id());
            }
            created.add(client);
        }
        return List.copyOf(created);
    }
}
