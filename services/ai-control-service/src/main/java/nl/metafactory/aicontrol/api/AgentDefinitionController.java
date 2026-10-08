package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.client.AgentSpecDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.PromptRequestDto;
import nl.metafactory.aicontrol.generated.api.AgentDefinitionApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class AgentDefinitionController implements AgentDefinitionApi {

    private final EmbabelAgentClient client;

    public AgentDefinitionController(EmbabelAgentClient client) {
        this.client = client;
    }

    @Override
    public ResponseEntity<List<AgentSpecDto>> listAgentDefinitions() {
        return ResponseEntity.ok(client.listAgentSpecs());
    }

    @Override
    public ResponseEntity<AgentSpecDto> get(String name) {
        AgentSpecDto spec = client.getAgentSpec(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent definition not found: " + name));
        return ResponseEntity.ok(spec);
    }

    @Override
    public ResponseEntity<AgentSpecDto> create(AgentSpecDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(client.createAgentSpec(request));
    }

    @Override
    public ResponseEntity<AgentSpecDto> update(String name, AgentSpecDto request) {
        return ResponseEntity.ok(client.updateAgentSpec(name, request));
    }

    @Override
    public ResponseEntity<Void> delete(String name) {
        client.deleteAgentSpec(name);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<AgentSpecDto> generateFromPrompt(PromptRequestDto request) {
        return ResponseEntity.ok(client.generateAgentSpec(request.prompt()));
    }
}
