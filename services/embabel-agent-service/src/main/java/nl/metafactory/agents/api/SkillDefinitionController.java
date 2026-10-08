package nl.metafactory.agents.api;

import nl.metafactory.agents.workflow.PromptToDefinitionService;
import nl.metafactory.agents.workflow.SkillSpecRepository;
import nl.metafactory.agents.workflow.model.PromptRequest;
import nl.metafactory.agents.workflow.model.SkillSpec;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/skill-definitions")
public class SkillDefinitionController {

    private final SkillSpecRepository repository;
    private final PromptToDefinitionService generationService;

    public SkillDefinitionController(SkillSpecRepository repository, PromptToDefinitionService generationService) {
        this.repository = repository;
        this.generationService = generationService;
    }

    @PostMapping("/generate-from-prompt")
    public SkillSpec generateFromPrompt(@RequestBody PromptRequest request) {
        return generationService.generateSkillSpec(request.prompt());
    }

    @GetMapping
    public List<SkillSpec> list() {
        return repository.findAll();
    }

    @GetMapping("/{name}")
    public SkillSpec get(@PathVariable String name) {
        return repository.findByName(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill definition not found: " + name));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SkillSpec create(@RequestBody SkillSpec spec) {
        return repository.save(spec);
    }

    @PutMapping("/{name}")
    public SkillSpec update(@PathVariable String name, @RequestBody SkillSpec spec) {
        repository.findByName(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill definition not found: " + name));
        return repository.save(spec);
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String name) {
        repository.deleteByName(name);
    }
}
