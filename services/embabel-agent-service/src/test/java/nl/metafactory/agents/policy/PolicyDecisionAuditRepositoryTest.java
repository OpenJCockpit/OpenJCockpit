package nl.metafactory.agents.policy;

import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.policy.model.PolicyDecisionAuditEntry;
import nl.metafactory.agents.workflow.YamlDefinitionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyDecisionAuditRepositoryTest {

    private PolicyDecisionAuditRepository repository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        var properties = new WorkflowDefinitionProperties();
        properties.setPath(tempDir.toString());
        repository = new PolicyDecisionAuditRepository(properties, new YamlDefinitionStore());
    }

    private PolicyDecisionAuditEntry entry(String id, String workflowId) {
        return new PolicyDecisionAuditEntry(id, workflowId, "exec-1", "project-1", "Project One",
                "customer-1", "Customer One", "requirements-agent", null, null, null,
                "dashboard-button", "workflow.start", "decision-1", "ALLOWED", "allowed", "low",
                false, List.of(), Instant.now(), null, false, null);
    }

    @Test
    void findAllReturnsEmptyWhenNoneSaved() {
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void saveThenFindAllRoundTrips() {
        var e = entry("audit-1", "wf-1");

        repository.save(e);

        assertThat(repository.findAll()).containsExactly(e);
    }

    @Test
    void multipleEntriesAreAllReturned() {
        repository.save(entry("audit-1", "wf-1"));
        repository.save(entry("audit-2", "wf-2"));

        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    void findAllSkipsAMalformedDecisionLogFileAndReturnsTheRemainingValidOnes(@TempDir Path tempDir) throws java.io.IOException {
        repository.save(entry("decision-1", "wf-1"));
        repository.save(entry("decision-2", "wf-2"));
        var decisionLogsDir = tempDir.resolve("decision-logs");
        java.nio.file.Files.createDirectories(decisionLogsDir);
        java.nio.file.Files.writeString(decisionLogsDir.resolve("zz-broken.yaml"), "- not\n- an\n- object\n");

        assertThat(repository.findAll()).hasSize(2);
    }
}
