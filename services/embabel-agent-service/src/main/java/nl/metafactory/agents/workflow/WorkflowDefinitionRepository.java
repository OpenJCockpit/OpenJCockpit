package nl.metafactory.agents.workflow;

import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class WorkflowDefinitionRepository {

    private final Path dir;
    private final YamlDefinitionStore store;

    public WorkflowDefinitionRepository(WorkflowDefinitionProperties properties, YamlDefinitionStore store) {
        this.dir = Path.of(properties.getPath(), "workflows");
        this.store = store;
    }

    public List<WorkflowDefinition> findAll() {
        return store.listWithKeys(dir, WorkflowDefinition.class).stream()
                .map(entry -> ensureId(entry.getKey(), entry.getValue()))
                .toList();
    }

    public Optional<WorkflowDefinition> findById(String id) {
        return store.find(dir, id, WorkflowDefinition.class)
                .map(definition -> ensureId(id, definition));
    }

    public WorkflowDefinition save(WorkflowDefinition definition) {
        WorkflowDefinition withId = hasId(definition) ? definition : withGeneratedId(definition);
        return persist(withId);
    }

    public boolean deleteById(String id) {
        return store.delete(dir, id);
    }

    /**
     * Repairs a definition that was persisted without an id (or whose id no longer matches
     * the file it was loaded from): without an id it can never be addressed again via the
     * {id}-based API routes, so on every load we generate one and re-save it under that id,
     * removing the now-superseded file.
     */
    private WorkflowDefinition ensureId(String storedKey, WorkflowDefinition definition) {
        if (hasId(definition)) {
            return definition;
        }
        WorkflowDefinition withId = withGeneratedId(definition);
        WorkflowDefinition saved = persist(withId);
        if (!withId.id().equals(storedKey)) {
            store.delete(dir, storedKey);
        }
        return saved;
    }

    /**
     * BR-5/BR-2 (workflow-execution-state-to-database): the definition file is configuration
     * only. lastExecutionStatus/lastExecutionAt are runtime state owned by agent_runs and are
     * stripped here — the single point through which every definition write passes — so no CRUD
     * write, import, group-unlink or ensureId repair can round-trip them back onto disk. Uses
     * the ADR-007 wither, so a future component cannot be dropped by this strip (BR-6).
     */
    private WorkflowDefinition persist(WorkflowDefinition definition) {
        return store.save(dir, definition.id(), definition.withLastExecution(null, null));
    }

    private boolean hasId(WorkflowDefinition definition) {
        return definition.id() != null && !definition.id().isBlank();
    }

    private WorkflowDefinition withGeneratedId(WorkflowDefinition definition) {
        String generatedId = "wf-" + UUID.randomUUID().toString().substring(0, 8);
        return definition.withId(generatedId);
    }
}
