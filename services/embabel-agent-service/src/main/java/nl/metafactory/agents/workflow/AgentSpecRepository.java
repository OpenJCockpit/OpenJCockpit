package nl.metafactory.agents.workflow;

import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.workflow.model.AgentSpec;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Component
public class AgentSpecRepository {

    private final Path dir;
    private final YamlDefinitionStore store;

    public AgentSpecRepository(WorkflowDefinitionProperties properties, YamlDefinitionStore store) {
        this.dir = Path.of(properties.getPath(), "agents");
        this.store = store;
    }

    public List<AgentSpec> findAll() {
        return store.list(dir, AgentSpec.class);
    }

    public Optional<AgentSpec> findByName(String name) {
        return store.find(dir, name, AgentSpec.class);
    }

    public AgentSpec save(AgentSpec spec) {
        return store.save(dir, spec.name(), spec);
    }

    public boolean deleteByName(String name) {
        return store.delete(dir, name);
    }
}
