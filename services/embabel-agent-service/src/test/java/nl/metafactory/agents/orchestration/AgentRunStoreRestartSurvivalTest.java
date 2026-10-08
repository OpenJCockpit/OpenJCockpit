package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.model.AgentRunSummaryPage;
import nl.metafactory.agents.persistence.JpaAgentRunPersistence;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The single most important test in this delivery: proves that a run's full history — status,
 * completedAt, every event in order, every artifact in order — survives a simulated process
 * restart of {@link AgentRunStore}, backed by a REAL {@link JpaAgentRunPersistence} against a REAL
 * H2 database with the REAL Flyway migration applied (mirroring
 * {@code JpaAgentRunPersistenceTest}'s own test-context setup). Deliberately does NOT use any
 * mock or in-memory fake persistence implementation — that would defeat the entire purpose of
 * this test.
 *
 * <p>A "restart" is simulated by constructing a brand-new {@link AgentRunStore} instance wired to
 * the SAME injected {@link JpaAgentRunPersistence} bean (i.e. the same underlying datasource) —
 * never reusing the first {@code AgentRunStore} instance, whose in-memory map is what a real
 * process restart would actually lose.
 */
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
@Import(JpaAgentRunPersistence.class)
class AgentRunStoreRestartSurvivalTest {

    @Autowired
    private JpaAgentRunPersistence persistence;

    @Test
    void runHistorySurvivesASimulatedRestartOfAgentRunStore() {
        AgentRunStore beforeRestart = new AgentRunStore(persistence, event -> { });

        beforeRestart.create("run-restart-1", "cust1", "spec.md", "https://example.invalid/repo.git",
                "wf-restart", "alice");

        beforeRestart.recordEvent("run-restart-1", "agent-a", "requirement analysis started", "IN_PROGRESS", "evidence://0");
        beforeRestart.recordEvent("run-restart-1", "agent-b", "impact analysis completed", "DONE", "evidence://1");
        beforeRestart.recordEvent("run-restart-1", "agent-c", "implementation completed", "DONE", "evidence://2");

        beforeRestart.addArtifact("run-restart-1", "git-branch:feature/restart-survival");
        beforeRestart.addArtifact("run-restart-1", "git-commit:abc1234");

        beforeRestart.setStatus("run-restart-1", "COMPLETED");

        AgentRun beforeRun = beforeRestart.get("run-restart-1");
        assertThat(beforeRun.completedAt()).isNotNull();

        // Simulate a restart: a brand-new AgentRunStore instance, same underlying persistence.
        AgentRunStore afterRestart = new AgentRunStore(persistence, event -> { });

        AgentRunSummaryPage page = afterRestart.listByWorkflow("wf-restart", 20, 0);
        assertThat(page.items()).hasSize(1);
        var summary = page.items().get(0);
        assertThat(summary.runId()).isEqualTo("run-restart-1");
        assertThat(summary.status()).isEqualTo("COMPLETED");
        assertThat(summary.completedAt()).isNotNull();
        assertThat(summary.startedBy()).isEqualTo("alice");

        Optional<AgentRun> found = afterRestart.find("run-restart-1");
        assertThat(found).isPresent();
        AgentRun restoredRun = found.get();

        assertThat(restoredRun.runId()).isEqualTo("run-restart-1");
        assertThat(restoredRun.workflowId()).isEqualTo("wf-restart");
        assertThat(restoredRun.customerId()).isEqualTo("cust1");
        assertThat(restoredRun.status()).isEqualTo("COMPLETED");
        assertThat(restoredRun.startedAt()).isEqualTo(beforeRun.startedAt());
        assertThat(restoredRun.completedAt()).isNotNull();
        assertThat(restoredRun.startedBy()).isEqualTo("alice");
        assertThat(restoredRun.status()).isNotEqualTo("RUN_STATE_LOST");

        assertThat(restoredRun.events()).hasSize(3);
        assertThat(restoredRun.events().get(0).title()).isEqualTo("requirement analysis started");
        assertThat(restoredRun.events().get(0).agentId()).isEqualTo("agent-a");
        assertThat(restoredRun.events().get(1).title()).isEqualTo("impact analysis completed");
        assertThat(restoredRun.events().get(1).agentId()).isEqualTo("agent-b");
        assertThat(restoredRun.events().get(2).title()).isEqualTo("implementation completed");
        assertThat(restoredRun.events().get(2).agentId()).isEqualTo("agent-c");

        assertThat(restoredRun.generatedArtifacts()).containsExactly(
                "git-branch:feature/restart-survival", "git-commit:abc1234");
    }
}
