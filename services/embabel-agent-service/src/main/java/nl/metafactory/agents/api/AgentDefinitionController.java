package nl.metafactory.agents.api;

import nl.metafactory.agents.workflow.AgentSpecRepository;
import nl.metafactory.agents.workflow.PromptToDefinitionService;
import nl.metafactory.agents.workflow.model.AgentSpec;
import nl.metafactory.agents.workflow.model.PromptRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** User-defined agent specs (persisted in {@link AgentSpecRepository}). */
@RestController
@RequestMapping("/api/agent-definitions")
public class AgentDefinitionController {

    private final AgentSpecRepository repository;
    private final PromptToDefinitionService generationService;

    public AgentDefinitionController(AgentSpecRepository repository, PromptToDefinitionService generationService) {
        this.repository = repository;
        this.generationService = generationService;
    }

    @PostMapping("/generate-from-prompt")
    public AgentSpec generateFromPrompt(@RequestBody PromptRequest request) {
        return generationService.generateAgentSpec(request.prompt());
    }

    @GetMapping
    public List<AgentSpec> list() {
        return repository.findAll();
    }

    @GetMapping("/{name}")
    public AgentSpec get(@PathVariable String name) {
        return repository.findByName(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent definition not found: " + name));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentSpec create(@RequestBody AgentSpec spec) {
        return repository.save(spec);
    }

    @PutMapping("/{name}")
    public AgentSpec update(@PathVariable String name, @RequestBody AgentSpec spec) {
        repository.findByName(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent definition not found: " + name));
        return repository.save(spec);
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String name) {
        repository.deleteByName(name);
    }
}
