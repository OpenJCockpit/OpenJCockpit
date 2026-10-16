package nl.metafactory.agents.catalog;

import nl.metafactory.agents.a2a.client.A2AProviderClient;
import nl.metafactory.agents.a2a.client.A2AProviderClients;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/** Read-only agent catalog: local {@code /agents} folder plus one section per A2A provider client. */
@RestController
public class AgentCatalogController {

    private final LocalAgentCatalog local;
    private final A2AProviderClients providers;

    public AgentCatalogController(LocalAgentCatalog local, A2AProviderClients providers) {
        this.local = local;
        this.providers = providers;
    }

    @GetMapping("/api/agent-catalog")
    public AgentCatalog catalog() {
        return new AgentCatalog(local.list(), providers.all().stream().map(AgentCatalogController::section).toList());
    }

    /** The agent card hosted by the provider, fetched live (agent details). */
    @GetMapping("/api/agent-catalog/providers/{provider}/agents/{name}/card")
    public ResponseEntity<JsonNode> card(@PathVariable String provider, @PathVariable String name) {
        var client = providers.find(provider)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown A2A provider"));
        var result = client.agentCard(name);
        return switch (result.status()) {
            case OK -> ResponseEntity.ok(result.card());
            case NOT_FOUND -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent card not found");
            case NOT_CONFIGURED -> throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A2A provider not configured");
            case UNAUTHORIZED -> throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The provider key may not read this agent card");
            default -> throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A2A provider unavailable");
        };
    }

    static AgentCatalog.ProviderSection section(A2AProviderClient client) {
        var listing = client.listAgents();
        if (listing.status() != A2AProviderClient.Status.OK) {
            String status = listing.status() == A2AProviderClient.Status.NOT_FOUND ? "UNAVAILABLE" : listing.status().name();
            return new AgentCatalog.ProviderSection(client.name(), client.id(), status,
                    client.name() + ": " + listing.reason(), java.util.List.of());
        }
        String message = listing.skippedInvalid() > 0
                ? listing.skippedInvalid() + " registry entr" + (listing.skippedInvalid() == 1 ? "y" : "ies")
                + " skipped: incomplete agent card" : null;
        return new AgentCatalog.ProviderSection(client.name(), client.id(), "OK", message,
                listing.agents().stream().map(a -> new AgentCatalog.ProviderAgent(a.id(), a.name(), a.description(),
                        a.url(), a.cardUrl(), a.backendUrl(), a.protocolVersion(), a.version(), a.streaming(),
                        a.skills())).toList());
    }
}
