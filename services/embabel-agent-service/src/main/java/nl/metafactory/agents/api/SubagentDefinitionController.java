package nl.metafactory.agents.api;

import nl.metafactory.agents.workflow.PromptToDefinitionService;
import nl.metafactory.agents.workflow.SubagentSpecRepository;
import nl.metafactory.agents.workflow.model.PromptRequest;
import nl.metafactory.agents.workflow.model.SubagentSpec;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/subagent-definitions")
public class SubagentDefinitionController {

    private final SubagentSpecRepository repository;
    private final PromptToDefinitionService generationService;

    public SubagentDefinitionController(SubagentSpecRepository repository, PromptToDefinitionService generationService) {
        this.repository = repository;
        this.generationService = generationService;
    }

    @PostMapping("/generate-from-prompt")
    public SubagentSpec generateFromPrompt(@RequestBody PromptRequest request) {
        return generationService.generateSubagentSpec(request.prompt());
    }

    @GetMapping
    public List<SubagentSpec> list() {
        return repository.findAll();
    }

    @GetMapping("/{name}")
    public SubagentSpec get(@PathVariable String name) {
        return repository.findByName(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subagent definition not found: " + name));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubagentSpec create(@RequestBody SubagentSpec spec) {
        return repository.save(spec);
    }

    @PutMapping("/{name}")
    public SubagentSpec update(@PathVariable String name, @RequestBody SubagentSpec spec) {
        repository.findByName(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subagent definition not found: " + name));
        return repository.save(spec);
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String name) {
        repository.deleteByName(name);
    }
}
