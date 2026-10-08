package nl.metafactory.agents.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;

@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
class AgentRunHistoryMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationAppliesCleanlyWithZeroSeedRows() {
        assertThat(count("agent_runs")).isZero();
        assertThat(count("agent_run_events")).isZero();
        assertThat(count("agent_run_artifacts")).isZero();
    }

    @Test
    void migrationRecordsExactlyOneSuccessfulFlywayEntryForV1() {
        Long successCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"success\" = true AND \"version\" = '1'",
                Long.class);
        assertThat(successCount).isEqualTo(1L);
    }

    private long count(String table) {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return result == null ? -1L : result;
    }
}
