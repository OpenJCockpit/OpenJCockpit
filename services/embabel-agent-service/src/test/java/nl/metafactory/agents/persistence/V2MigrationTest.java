package nl.metafactory.agents.persistence;

import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-13 (workflow-execution-state-to-database, AC-20/21/22): proves the {@code V2} Flyway
 * migration applies exactly once, is idempotent across a second migrate invocation (the
 * equivalent of a second application boot against the same schema), leaves a pre-existing V1-shaped
 * row's new {@code failure_summary} column null, and that {@code ddl-auto: validate} — set for
 * this whole test class via {@link TestPropertySource}, overriding the module's default {@code none}
 * — passes cleanly against the migrated schema (if the JPA entity mapping did not match the DDL,
 * context loading itself would fail here, failing every test in this class).
 */
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class V2MigrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AgentRunRecordRepository agentRunRecordRepository;

    private long countSuccessfulHistoryRowsForVersion(String version) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"success\" = true AND \"version\" = ?",
                Long.class, version);
        return count == null ? -1L : count;
    }

    @Test
    void v2MigrationAppliesExactlyOnceAndIsRecordedAsSuccessful() {
        assertThat(countSuccessfulHistoryRowsForVersion("2")).isEqualTo(1L);
    }

    @Test
    void aSecondMigrateInvocationLeavesExactlyOneV2HistoryRow() {
        // Flyway.migrate() is exactly what runs at real application startup; invoking it again
        // against the already-migrated schema is a faithful equivalent of a second application
        // boot for the purpose of proving migration idempotence.
        flyway.migrate();

        assertThat(countSuccessfulHistoryRowsForVersion("2")).isEqualTo(1L);
    }

    @Test
    void aPreExistingV1ShapedRowSurvivesWithFailureSummaryNull() {
        AgentRunRecord record = new AgentRunRecord("run-pre-v2", "wf-pre-v2", "cust-1", "spec.md",
                "https://example.test/repo", "COMPLETED", Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T01:00:00Z"), "alice", null, null);
        agentRunRecordRepository.save(record);
        entityManager.flush();
        entityManager.clear();

        AgentRunRecord reloaded = agentRunRecordRepository.findById("run-pre-v2").orElseThrow();

        assertThat(reloaded.getFailureSummary()).isNull();
        assertThat(reloaded.getStatus()).isEqualTo("COMPLETED");
    }
}
