package nl.metafactory.agents.approval;

import nl.metafactory.agents.approval.model.ApprovalDecisionAuditEntry;
import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.workflow.YamlDefinitionStore;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * File-based store for approval-decision audit entries (ADR-004) — a directory sibling to the
 * pre-existing policy decision-log store, reusing the same {@link YamlDefinitionStore}
 * infrastructure and the same configured root ({@code metafactory.workflow-definitions.path}).
 * No new volume, no migration.
 *
 * <p>Key = {@code runId + "-" + iteration (%03d)} — deterministic, so a hypothetical duplicate
 * write for the same iteration overwrites instead of duplicating, giving AC-31 a storage-layer
 * backstop underneath the 409 (BR-12/AC-31).</p>
 */
@Component
public class ApprovalDecisionAuditRepository {

    private final Path dir;
    private final YamlDefinitionStore store;

    public ApprovalDecisionAuditRepository(WorkflowDefinitionProperties properties, YamlDefinitionStore store) {
        this.dir = Path.of(properties.getPath(), "approval-decisions");
        this.store = store;
    }

    public ApprovalDecisionAuditEntry save(ApprovalDecisionAuditEntry entry) {
        String key = entry.runId() + "-" + String.format("%03d", entry.iteration());
        return store.save(dir, key, entry);
    }

    /** Chronological order by iteration (AC-44/AC-45), so the whole review conversation is reconstructable. */
    public List<ApprovalDecisionAuditEntry> findByRunId(String runId) {
        return store.list(dir, ApprovalDecisionAuditEntry.class).stream()
                .filter(entry -> runId.equals(entry.runId()))
                .sorted(Comparator.comparingInt(ApprovalDecisionAuditEntry::iteration))
                .toList();
    }
}
