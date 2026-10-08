package nl.metafactory.agents.api;

import nl.metafactory.agents.workflow.WorkflowDefinitionRepository;
import nl.metafactory.agents.workflow.WorkflowGroupRepository;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowGroup;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/workflow-groups")
public class WorkflowGroupController {

    private final WorkflowGroupRepository repository;
    private final WorkflowDefinitionRepository workflowRepository;

    public WorkflowGroupController(WorkflowGroupRepository repository,
                                   WorkflowDefinitionRepository workflowRepository) {
        this.repository = repository;
        this.workflowRepository = workflowRepository;
    }

    @GetMapping
    public List<WorkflowGroup> list() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public WorkflowGroup get(@PathVariable String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow group not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowGroup create(@RequestBody WorkflowGroup group) {
        return repository.save(group);
    }

    @PutMapping("/{id}")
    public WorkflowGroup update(@PathVariable String id, @RequestBody WorkflowGroup group) {
        repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow group not found: " + id));
        return repository.save(group);
    }

    /** Deletes the group and removes the link from workflows that reference it. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        repository.deleteById(id);
        workflowRepository.findAll().stream()
                .filter(workflow -> id.equals(workflow.groupId()))
                .forEach(workflow -> workflowRepository.save(withoutGroup(workflow)));
    }

    private WorkflowDefinition withoutGroup(WorkflowDefinition workflow) {
        return workflow.withGroupId(null);
    }
}
