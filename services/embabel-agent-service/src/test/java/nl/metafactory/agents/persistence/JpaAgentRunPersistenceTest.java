package nl.metafactory.agents.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.context.annotation.Import;

@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
@Import(JpaAgentRunPersistence.class)
class JpaAgentRunPersistenceTest {

    @Autowired
    private JpaAgentRunPersistence persistence;

    @Autowired
    private AgentRunRecordRepository agentRunRecordRepository;

    @Test
    void recordNewPersistsAndMasksRepositoryUrl() {
        persistence.recordNew("run-1", "wf-1", "cust-1", "spec.yaml",
                "https://user:token@github.com/org/repo.git", "RUNNING", Instant.now(), "alice");

        var entity = agentRunRecordRepository.findById("run-1").orElseThrow();
        assertThat(entity.getRepositoryUrl()).isEqualTo("https://***@github.com/org/repo.git");
    }

    @Test
    void appendEventPropagatesConstraintViolationForUnknownRun() {
        assertThrows(Exception.class,
                () -> persistence.appendEvent("nonexistent-run", 0, Instant.now(), "agent-a", "title", "STATUS", null));
    }

    @Test
    void flushTerminalIsIdempotentAndInsertsOnlyMissingRows() {
        persistence.recordNew("run-2", "wf-1", null, null, null, "RUNNING", Instant.now(), "bob");
        persistence.appendEvent("run-2", 0, Instant.now(), "agent-a", "step 0", "DONE", null);

        List<AgentRunPersistencePort.EventSnapshot> events = List.of(
                new AgentRunPersistencePort.EventSnapshot(0, Instant.now(), "agent-a", "step 0", "DONE", null),
                new AgentRunPersistencePort.EventSnapshot(1, Instant.now(), "agent-b", "step 1", "DONE", null));
        List<String> artifacts = List.of("artifact-0");

        persistence.flushTerminal("run-2", "COMPLETED", Instant.now(), null, events, artifacts);

        var full = persistence.findFullRun("run-2").orElseThrow();
        assertThat(full.status()).isEqualTo("COMPLETED");
        assertThat(full.events()).hasSize(2);
        assertThat(full.artifacts()).hasSize(1);

        persistence.flushTerminal("run-2", "COMPLETED", Instant.now(), null, events, artifacts);

        var fullAgain = persistence.findFullRun("run-2").orElseThrow();
        assertThat(fullAgain.events()).hasSize(2);
        assertThat(fullAgain.artifacts()).hasSize(1);
    }

    @Test
    void flushTerminalNeverFabricatesCompletedAtWhenGivenNull() {
        persistence.recordNew("run-3", "wf-1", null, null, null, "RUNNING", Instant.now(), null);
        persistence.flushTerminal("run-3", "RUN_STATE_LOST", null, null, List.of(), List.of());

        assertThat(persistence.findFullRun("run-3").orElseThrow().completedAt()).isNull();
    }

    @Test
    void findFullRunReturnsEmptyForUnknownId() {
        assertThat(persistence.findFullRun("does-not-exist")).isEmpty();
    }

    @Test
    void listByWorkflowReturnsSummaryOnlyPageOrderedNewestFirst() {
        persistence.recordNew("run-old", "wf-listing", null, null, null, "COMPLETED", Instant.now().minusSeconds(60), null);
        persistence.recordNew("run-new", "wf-listing", null, null, null, "RUNNING", Instant.now(), null);

        var page = persistence.listByWorkflow("wf-listing", 10, 0);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.items().get(0).runId()).isEqualTo("run-new");
    }

    @Test
    void appendArtifactPersistsRecordForKnownRun() {
        persistence.recordNew("run-artifact-1", "wf-1", null, null, null, "RUNNING", Instant.now(), null);
        persistence.appendArtifact("run-artifact-1", 0, "artifact-x");

        var full = persistence.findFullRun("run-artifact-1").orElseThrow();
        assertThat(full.artifacts()).containsExactly("artifact-x");
    }

    @Test
    void flushTerminalThrowsForUnknownRun() {
        assertThrows(Exception.class,
                () -> persistence.flushTerminal("does-not-exist-run", "COMPLETED", Instant.now(), null, List.of(), List.of()));
    }

    @Test
    void flushTerminalWriteOnceNeverErasesAPreviouslyRecordedFailureSummary() {
        persistence.recordNew("run-write-once", "wf-1", null, null, null, "RUNNING", Instant.now(), null);

        persistence.flushTerminal("run-write-once", "FAILED", Instant.now(), "IllegalStateException", List.of(), List.of());
        var firstFlush = persistence.findFullRun("run-write-once").orElseThrow();
        assertThat(firstFlush.failureSummary()).isEqualTo("IllegalStateException");

        persistence.flushTerminal("run-write-once", "FAILED", Instant.now(), null, List.of(), List.of());
        var secondFlush = persistence.findFullRun("run-write-once").orElseThrow();
        assertThat(secondFlush.failureSummary()).isEqualTo("IllegalStateException");
    }
}
