package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.PromptRequestDto;
import nl.metafactory.aicontrol.client.SubagentSpecDto;
import nl.metafactory.aicontrol.generated.api.SubagentDefinitionApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class SubagentDefinitionController implements SubagentDefinitionApi {

    private final EmbabelAgentClient client;

    public SubagentDefinitionController(EmbabelAgentClient client) {
        this.client = client;
    }

    @Override
    public ResponseEntity<List<SubagentSpecDto>> listSubagentDefinitions() {
        return ResponseEntity.ok(client.listSubagentSpecs());
    }

    @Override
    public ResponseEntity<SubagentSpecDto> get(String name) {
        SubagentSpecDto spec = client.getSubagentSpec(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subagent definition not found: " + name));
        return ResponseEntity.ok(spec);
    }

    @Override
    public ResponseEntity<SubagentSpecDto> create(SubagentSpecDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(client.createSubagentSpec(request));
    }

    @Override
    public ResponseEntity<SubagentSpecDto> update(String name, SubagentSpecDto request) {
        return ResponseEntity.ok(client.updateSubagentSpec(name, request));
    }

    @Override
    public ResponseEntity<Void> delete(String name) {
        client.deleteSubagentSpec(name);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<SubagentSpecDto> generateFromPrompt(PromptRequestDto request) {
        return ResponseEntity.ok(client.generateSubagentSpec(request.prompt()));
    }
}
