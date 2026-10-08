package nl.metafactory.agents.policy;

import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.policy.model.PolicyDecisionAuditEntry;
import nl.metafactory.agents.workflow.YamlDefinitionStore;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * File-based store for policy decision audit entries — this is the backend's own audit trail,
 * independent of whatever remote decision logs OPA itself may keep. Reuses the same
 * YamlDefinitionStore infrastructure as workflow/agent/subagent/skill definitions, under a
 * dedicated decision-logs/ subdirectory of the same configured root.
 */
@Component
public class PolicyDecisionAuditRepository {

    private final Path dir;
    private final YamlDefinitionStore store;

    public PolicyDecisionAuditRepository(WorkflowDefinitionProperties properties, YamlDefinitionStore store) {
        this.dir = Path.of(properties.getPath(), "decision-logs");
        this.store = store;
    }

    public PolicyDecisionAuditEntry save(PolicyDecisionAuditEntry entry) {
        return store.save(dir, entry.id(), entry);
    }

    public List<PolicyDecisionAuditEntry> findAll() {
        return store.list(dir, PolicyDecisionAuditEntry.class);
    }
}
