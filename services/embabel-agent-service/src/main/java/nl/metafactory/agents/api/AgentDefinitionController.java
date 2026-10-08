package nl.metafactory.agents.api;

import nl.metafactory.agents.model.AgentDefinition;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import nl.metafactory.agents.workflow.AgentSpecRepository;
import nl.metafactory.agents.workflow.PromptToDefinitionService;
import nl.metafactory.agents.workflow.model.AgentSpec;
import nl.metafactory.agents.workflow.model.PromptRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Exposes both user-defined agent specs (persisted in {@link AgentSpecRepository}) and the
 * built-in pipeline-stage agents from {@link AgentOrchestrator#availableAgents()} — the latter
 * actually execute every workflow's {@code agentIds} but were never otherwise visible to users.
 * Built-in entries are synthesized on read, not persisted, and cannot be created/updated/deleted.
 */
@RestController
@RequestMapping("/api/agent-definitions")
public class AgentDefinitionController {

    private static final Map<String, List<String>> BUILT_IN_SUBAGENTS = Map.of(
            "implementation", List.of("software-architect-agent", "developer-agent", "lead-developer-agent"),
            "realisation", List.of("code-realisation-agent")
    );

    private final AgentSpecRepository repository;
    private final PromptToDefinitionService generationService;
    private final AgentOrchestrator orchestrator;

    public AgentDefinitionController(AgentSpecRepository repository, PromptToDefinitionService generationService,
                                      AgentOrchestrator orchestrator) {
        this.repository = repository;
        this.generationService = generationService;
        this.orchestrator = orchestrator;
    }

    @PostMapping("/generate-from-prompt")
    public AgentSpec generateFromPrompt(@RequestBody PromptRequest request) {
        return generationService.generateAgentSpec(request.prompt());
    }

    @GetMapping
    public List<AgentSpec> list() {
        return Stream.concat(repository.findAll().stream(), builtInAgents().stream()).toList();
    }

    @GetMapping("/{name}")
    public AgentSpec get(@PathVariable String name) {
        return repository.findByName(name)
                .or(() -> findBuiltIn(name))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent definition not found: " + name));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentSpec create(@RequestBody AgentSpec spec) {
        return repository.save(spec);
    }

    @PutMapping("/{name}")
    public AgentSpec update(@PathVariable String name, @RequestBody AgentSpec spec) {
        rejectBuiltIn(name);
        repository.findByName(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent definition not found: " + name));
        return repository.save(spec);
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String name) {
        rejectBuiltIn(name);
        repository.deleteByName(name);
    }

    private void rejectBuiltIn(String name) {
        if (findBuiltIn(name).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Built-in agent definitions cannot be modified: " + name);
        }
    }

    private Optional<AgentSpec> findBuiltIn(String name) {
        return builtInAgents().stream().filter(spec -> spec.name().equals(name)).findFirst();
    }

    private List<AgentSpec> builtInAgents() {
        return orchestrator.availableAgents().stream().map(this::toBuiltInSpec).toList();
    }

    private AgentSpec toBuiltInSpec(AgentDefinition definition) {
        return new AgentSpec(
                definition.id(),
                definition.description(),
                definition.role(),
                "Built-in pipeline stage — runs as part of any workflow that includes it in agentIds.",
                BUILT_IN_SUBAGENTS.getOrDefault(definition.id(), List.of()),
                definition.requiredOutputs(),
                List.of(),
                null,
                true);
    }
}
