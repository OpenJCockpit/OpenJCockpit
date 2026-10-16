package nl.metafactory.agents.workflow.agents;

import nl.metafactory.agents.a2a.client.A2AProviderClient;
import nl.metafactory.agents.a2a.client.A2AProviderClients;
import nl.metafactory.agents.catalog.LocalAgentCatalog;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Verifies references against the same catalog the Agents tab shows: a {@code local:} reference
 * needs a valid agent in the repository {@code /agents} folder; an {@code a2a:} reference needs a
 * configured provider that lists the agent. When the provider cannot be reached the reference is
 * accepted (it is checked again when the workflow runs).
 */
@Component
public class CatalogAgentReferenceCheck implements AgentReferenceCheck {

    private final LocalAgentCatalog local;
    private final A2AProviderClients providers;

    public CatalogAgentReferenceCheck(LocalAgentCatalog local, A2AProviderClients providers) {
        this.local = local;
        this.providers = providers;
    }

    @Override
    public Optional<String> problem(AgentRef ref) {
        if (ref.source() == AgentRef.Source.LOCAL) {
            boolean found = local.list().agents().stream()
                    .anyMatch(a -> a.valid() && a.id().equals(ref.agentId()));
            return found ? Optional.empty()
                    : Optional.of("Local agent '" + ref.agentId() + "' does not exist in the /agents folder");
        }
        var client = providers.find(ref.provider());
        if (client.isEmpty()) {
            return Optional.of("A2A provider '" + ref.provider() + "' is not configured");
        }
        var listing = client.get().listAgents();
        if (listing.status() != A2AProviderClient.Status.OK) {
            return Optional.empty();
        }
        boolean found = listing.agents().stream().map(A2AProviderClient.Agent::name).anyMatch(ref.agentId()::equals);
        return found ? Optional.empty()
                : Optional.of("Agent '" + ref.agentId() + "' is not available at A2A provider " + client.get().name());
    }
}
