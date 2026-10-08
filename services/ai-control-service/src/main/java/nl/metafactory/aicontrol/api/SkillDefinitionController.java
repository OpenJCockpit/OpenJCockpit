package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.PromptRequestDto;
import nl.metafactory.aicontrol.client.SkillSpecDto;
import nl.metafactory.aicontrol.generated.api.SkillDefinitionApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class SkillDefinitionController implements SkillDefinitionApi {

    private final EmbabelAgentClient client;

    public SkillDefinitionController(EmbabelAgentClient client) {
        this.client = client;
    }

    @Override
    public ResponseEntity<List<SkillSpecDto>> listSkillDefinitions() {
        return ResponseEntity.ok(client.listSkillSpecs());
    }

    @Override
    public ResponseEntity<SkillSpecDto> get(String name) {
        SkillSpecDto spec = client.getSkillSpec(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill definition not found: " + name));
        return ResponseEntity.ok(spec);
    }

    @Override
    public ResponseEntity<SkillSpecDto> create(SkillSpecDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(client.createSkillSpec(request));
    }

    @Override
    public ResponseEntity<SkillSpecDto> update(String name, SkillSpecDto request) {
        return ResponseEntity.ok(client.updateSkillSpec(name, request));
    }

    @Override
    public ResponseEntity<Void> delete(String name) {
        client.deleteSkillSpec(name);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<SkillSpecDto> generateFromPrompt(PromptRequestDto request) {
        return ResponseEntity.ok(client.generateSkillSpec(request.prompt()));
    }
}
