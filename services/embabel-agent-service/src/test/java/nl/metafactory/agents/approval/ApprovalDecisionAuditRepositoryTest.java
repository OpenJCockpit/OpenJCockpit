package nl.metafactory.agents.approval;

import nl.metafactory.agents.approval.model.ApprovalDecisionAuditEntry;
import nl.metafactory.agents.approval.model.ApprovalDecisionKind;
import nl.metafactory.agents.approval.model.StageOutcome;
import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.workflow.YamlDefinitionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** AC-43 (durability), AC-44 (field completeness), AC-45 (chronological ordering). */
class ApprovalDecisionAuditRepositoryTest {

    private ApprovalDecisionAuditRepository repository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        var properties = new WorkflowDefinitionProperties();
        properties.setPath(tempDir.toString());
        repository = new ApprovalDecisionAuditRepository(properties, new YamlDefinitionStore());
    }

    private ApprovalDecisionAuditEntry entry(String runId, int iteration, ApprovalDecisionKind decision) {
        return new ApprovalDecisionAuditEntry("id-" + runId + "-" + iteration, runId, "wf-1", "realisation",
                iteration, decision, decision == ApprovalDecisionKind.ACCEPT_WITH_COMMENTS ? "please fix x" : null,
                "alice", "sub-1", StageOutcome.PUBLISHED, "feat/wf-1-run",
                "https://github.com/org/repo/pull/9", Instant.now());
    }

    @Test
    void saveThenFindByRunIdRoundTrips() {
        repository.save(entry("run-1", 1, ApprovalDecisionKind.ACCEPT));

        var found = repository.findByRunId("run-1");

        assertThat(found).hasSize(1);
        var saved = found.get(0);
        assertThat(saved.runId()).isEqualTo("run-1");
        assertThat(saved.workflowId()).isEqualTo("wf-1");
        assertThat(saved.gateStage()).isEqualTo("realisation");
        assertThat(saved.iteration()).isEqualTo(1);
        assertThat(saved.decision()).isEqualTo(ApprovalDecisionKind.ACCEPT);
        assertThat(saved.actorUsername()).isEqualTo("alice");
        assertThat(saved.actorSubject()).isEqualTo("sub-1");
        assertThat(saved.stageOutcome()).isEqualTo(StageOutcome.PUBLISHED);
        assertThat(saved.branchName()).isEqualTo("feat/wf-1-run");
        assertThat(saved.pullRequestUrl()).isEqualTo("https://github.com/org/repo/pull/9");
        assertThat(saved.timestamp()).isNotNull();
    }

    @Test
    void findByRunIdReturnsEntriesInChronologicalIterationOrder() {
        repository.save(entry("run-1", 3, ApprovalDecisionKind.ACCEPT));
        repository.save(entry("run-1", 1, ApprovalDecisionKind.ACCEPT_WITH_COMMENTS));
        repository.save(entry("run-1", 2, ApprovalDecisionKind.ACCEPT_WITH_COMMENTS));

        var found = repository.findByRunId("run-1");

        assertThat(found).extracting(ApprovalDecisionAuditEntry::iteration).containsExactly(1, 2, 3);
    }

    @Test
    void findByRunIdOnlyReturnsEntriesForThatRun() {
        repository.save(entry("run-1", 1, ApprovalDecisionKind.ACCEPT));
        repository.save(entry("run-2", 1, ApprovalDecisionKind.DENY));

        assertThat(repository.findByRunId("run-1")).extracting(ApprovalDecisionAuditEntry::runId)
                .containsExactly("run-1");
    }

    @Test
    void findByRunIdReturnsEmptyForAnUnknownRun() {
        assertThat(repository.findByRunId("missing")).isEmpty();
    }

    @Test
    void savingTheSameIterationTwiceOverwritesRatherThanDuplicates() {
        // AC-31's storage-layer backstop: deterministic key = runId-iteration(%03d).
        repository.save(entry("run-1", 1, ApprovalDecisionKind.ACCEPT_WITH_COMMENTS));
        repository.save(entry("run-1", 1, ApprovalDecisionKind.ACCEPT));

        var found = repository.findByRunId("run-1");

        assertThat(found).hasSize(1);
        assertThat(found.get(0).decision()).isEqualTo(ApprovalDecisionKind.ACCEPT);
    }

    @Test
    void commentIsPersistedOnlyForAcceptWithComments() {
        repository.save(entry("run-1", 1, ApprovalDecisionKind.ACCEPT_WITH_COMMENTS));

        assertThat(repository.findByRunId("run-1").get(0).comment()).isEqualTo("please fix x");
    }

    @Test
    void findByRunIdSkipsAMalformedFileElsewhereInTheDirectoryAndStillReturnsTheRequestedRunsEntries(@TempDir Path tempDir) throws java.io.IOException {
        repository.save(entry("run-1", 1, ApprovalDecisionKind.ACCEPT));
        var approvalDecisionsDir = tempDir.resolve("approval-decisions");
        java.nio.file.Files.createDirectories(approvalDecisionsDir);
        java.nio.file.Files.writeString(approvalDecisionsDir.resolve("zz-broken.yaml"), "- not\n- an\n- object\n");

        var found = repository.findByRunId("run-1");

        assertThat(found).hasSize(1);
        assertThat(found.get(0).runId()).isEqualTo("run-1");
    }
}
