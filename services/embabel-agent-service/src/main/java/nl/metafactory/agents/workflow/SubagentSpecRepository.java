package nl.metafactory.agents.workflow;

import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.workflow.model.SubagentSpec;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Component
public class SubagentSpecRepository {

    private final Path dir;
    private final YamlDefinitionStore store;

    public SubagentSpecRepository(WorkflowDefinitionProperties properties, YamlDefinitionStore store) {
        this.dir = Path.of(properties.getPath(), "subagents");
        this.store = store;
    }

    public List<SubagentSpec> findAll() {
        return store.list(dir, SubagentSpec.class);
    }

    public Optional<SubagentSpec> findByName(String name) {
        return store.find(dir, name, SubagentSpec.class);
    }

    public SubagentSpec save(SubagentSpec spec) {
        return store.save(dir, spec.name(), spec);
    }

    public boolean deleteByName(String name) {
        return store.delete(dir, name);
    }
}
