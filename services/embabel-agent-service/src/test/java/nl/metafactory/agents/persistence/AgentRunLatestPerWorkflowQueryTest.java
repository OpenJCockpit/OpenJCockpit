package nl.metafactory.agents.persistence;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-09/T-10 (workflow-execution-state-to-database): end-to-end proof of
 * {@link AgentRunHistoryQueryRepository#findLatestRunPerWorkflow(java.util.Collection)} and of
 * {@link JpaAgentRunPersistence#findLatestRunPerWorkflow(java.util.Collection)} against a real
 * H2-in-PostgreSQL-mode database — the same JPQL verified empirically in V3 of the lead's work
 * plan.
 */
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
@Import(JpaAgentRunPersistence.class)
class AgentRunLatestPerWorkflowQueryTest {

    @Autowired
    private AgentRunRecordRepository agentRunRecordRepository;

    @Autowired
    private AgentRunHistoryQueryRepository queryRepository;

    @Autowired
    private JpaAgentRunPersistence persistence;

    @Autowired
    private EntityManager entityManager;

    private static AgentRunRecord record(String runId, String workflowId, String status, Instant startedAt) {
        return new AgentRunRecord(runId, workflowId, null, null, null, status, startedAt, null, null, null, null);
    }

    private Statistics statistics() {
        SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        return statistics;
    }

    @Test
    void aWorkflowIdWithNoRunsIsAbsentFromTheResultNeverFabricated() {
        agentRunRecordRepository.save(record("run-other", "wf-has-runs", "COMPLETED", Instant.now()));
        entityManager.flush();

        Map<String, WorkflowLastExecutionProjection> result = indexById(
                queryRepository.findLatestRunPerWorkflow(List.of("wf-never-run")));

        assertThat(result).doesNotContainKey("wf-never-run");
        assertThat(result).isEmpty();
    }

    @Test
    void threeRunsForOneWorkflowResolveToTheGreatestStartedAt() {
        Instant base = Instant.parse("2024-01-01T00:00:00Z");
        agentRunRecordRepository.save(record("run-1", "wf-1", "COMPLETED", base));
        agentRunRecordRepository.save(record("run-2", "wf-1", "COMPLETED", base.plusSeconds(60)));
        agentRunRecordRepository.save(record("run-3", "wf-1", "RUNNING", base.plusSeconds(120)));
        entityManager.flush();

        Map<String, WorkflowLastExecutionProjection> result = indexById(
                queryRepository.findLatestRunPerWorkflow(List.of("wf-1")));

        assertThat(result).containsKey("wf-1");
        assertThat(result.get("wf-1").getStatus()).isEqualTo("RUNNING");
        assertThat(result.get("wf-1").getStartedAt()).isEqualTo(base.plusSeconds(120));
    }

    @Test
    void aTieOnStartedAtIsBrokenByTheGreatestRunIdCollationIndependently() {
        // R12: ASCII-only, unambiguously-ordered run ids so the tie-break assertion does not
        // depend on database collation.
        Instant tiedInstant = Instant.parse("2024-02-01T10:00:00Z");
        agentRunRecordRepository.save(record("run-a2", "wf-tie", "FAILED", tiedInstant));
        agentRunRecordRepository.save(record("run-a3", "wf-tie", "RUNNING", tiedInstant));
        entityManager.flush();

        Map<String, WorkflowLastExecutionProjection> result = indexById(
                queryRepository.findLatestRunPerWorkflow(List.of("wf-tie")));

        assertThat(result.get("wf-tie").getStatus()).isEqualTo("RUNNING");
    }

    @Test
    void aRunStateLostStatusIsIncludedWithNoStatusFiltering() {
        Instant startedAt = Instant.parse("2024-03-01T00:00:00Z");
        agentRunRecordRepository.save(record("run-lost", "wf-lost", "RUN_STATE_LOST", startedAt));
        entityManager.flush();

        Map<String, WorkflowLastExecutionProjection> result = indexById(
                queryRepository.findLatestRunPerWorkflow(List.of("wf-lost")));

        assertThat(result.get("wf-lost").getStatus()).isEqualTo("RUN_STATE_LOST");
    }

    @Test
    void exactlyOneQueryIsIssuedForUpToFiveHundredWorkflowIds() {
        agentRunRecordRepository.save(record("run-stat-1", "wf-stat-1", "COMPLETED", Instant.now()));
        agentRunRecordRepository.save(record("run-stat-2", "wf-stat-2", "COMPLETED", Instant.now()));
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = statistics();

        Map<String, AgentRunPersistencePort.LastExecution> result =
                persistence.findLatestRunPerWorkflow(List.of("wf-stat-1", "wf-stat-2"));

        assertThat(result).hasSize(2);
        assertThat(statistics.getQueryExecutionCount()).isEqualTo(1);
    }

    @Test
    void zeroQueriesAreIssuedForAnEmptyOrNullWorkflowIdCollection() {
        Statistics statistics = statistics();

        Map<String, AgentRunPersistencePort.LastExecution> emptyResult = persistence.findLatestRunPerWorkflow(List.of());
        Map<String, AgentRunPersistencePort.LastExecution> nullResult = persistence.findLatestRunPerWorkflow(null);

        assertThat(emptyResult).isEmpty();
        assertThat(nullResult).isEmpty();
        assertThat(statistics.getQueryExecutionCount()).isEqualTo(0);
    }

    private static Map<String, WorkflowLastExecutionProjection> indexById(List<WorkflowLastExecutionProjection> projections) {
        Map<String, WorkflowLastExecutionProjection> result = new java.util.HashMap<>();
        for (WorkflowLastExecutionProjection projection : projections) {
            result.put(projection.getWorkflowId(), projection);
        }
        return result;
    }
}
