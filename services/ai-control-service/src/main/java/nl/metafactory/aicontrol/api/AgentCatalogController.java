package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.client.AgentCatalogDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.generated.api.AgentCatalogApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Relays the read-only agent catalog (local /agents folder + A2A providers) to the dashboard. */
@RestController
public class AgentCatalogController implements AgentCatalogApi {

    private final EmbabelAgentClient client;

    public AgentCatalogController(EmbabelAgentClient client) {
        this.client = client;
    }

    @Override
    public ResponseEntity<AgentCatalogDto> getAgentCatalog() {
        return client.getAgentCatalog().map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Agent service unreachable"));
    }

    @Override
    public ResponseEntity<Map<String, Object>> getProviderAgentCard(String provider, String name) {
        var result = client.getProviderAgentCard(provider, name);
        if (result.status() == 200) {
            return ResponseEntity.ok(result.card());
        }
        throw new ResponseStatusException(HttpStatus.valueOf(result.status()),
                result.status() == 404 ? "Agent card not found" : "Agent card unavailable");
    }
}
