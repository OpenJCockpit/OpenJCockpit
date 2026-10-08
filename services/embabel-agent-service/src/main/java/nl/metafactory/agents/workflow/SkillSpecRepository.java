package nl.metafactory.agents.workflow;

import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.workflow.model.SkillSpec;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Component
public class SkillSpecRepository {

    private final Path dir;
    private final YamlDefinitionStore store;

    public SkillSpecRepository(WorkflowDefinitionProperties properties, YamlDefinitionStore store) {
        this.dir = Path.of(properties.getPath(), "skills");
        this.store = store;
    }

    public List<SkillSpec> findAll() {
        return store.list(dir, SkillSpec.class);
    }

    public Optional<SkillSpec> findByName(String name) {
        return store.find(dir, name, SkillSpec.class);
    }

    public SkillSpec save(SkillSpec spec) {
        return store.save(dir, spec.name(), spec);
    }

    public boolean deleteByName(String name) {
        return store.delete(dir, name);
    }
}
