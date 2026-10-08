package nl.metafactory.agents.workflow;

import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.workflow.model.WorkflowGroup;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class WorkflowGroupRepository {

    private final Path dir;
    private final YamlDefinitionStore store;

    public WorkflowGroupRepository(WorkflowDefinitionProperties properties, YamlDefinitionStore store) {
        this.dir = Path.of(properties.getPath(), "workflow-groups");
        this.store = store;
    }

    public List<WorkflowGroup> findAll() {
        return store.listWithKeys(dir, WorkflowGroup.class).stream()
                .map(entry -> ensureId(entry.getKey(), entry.getValue()))
                .toList();
    }

    public Optional<WorkflowGroup> findById(String id) {
        return store.find(dir, id, WorkflowGroup.class)
                .map(group -> ensureId(id, group));
    }

    public WorkflowGroup save(WorkflowGroup group) {
        WorkflowGroup withId = hasId(group) ? group : withGeneratedId(group);
        return store.save(dir, withId.id(), withId);
    }

    public boolean deleteById(String id) {
        return store.delete(dir, id);
    }

    // Same repair as WorkflowDefinitionRepository: a group without an id is unreachable via the
    // {id} routes, so on load we generate one and save again.
    private WorkflowGroup ensureId(String storedKey, WorkflowGroup group) {
        if (hasId(group)) {
            return group;
        }
        WorkflowGroup withId = withGeneratedId(group);
        WorkflowGroup saved = store.save(dir, withId.id(), withId);
        if (!withId.id().equals(storedKey)) {
            store.delete(dir, storedKey);
        }
        return saved;
    }

    private boolean hasId(WorkflowGroup group) {
        return group.id() != null && !group.id().isBlank();
    }

    private WorkflowGroup withGeneratedId(WorkflowGroup group) {
        String generatedId = "wg-" + UUID.randomUUID().toString().substring(0, 8);
        return new WorkflowGroup(generatedId, group.name(), group.description(), group.projectName());
    }
}
