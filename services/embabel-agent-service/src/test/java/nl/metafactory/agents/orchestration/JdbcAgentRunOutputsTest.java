package nl.metafactory.agents.orchestration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** V5: full agent outputs per run, ordered, replaceable, removed with the run. */
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
class JdbcAgentRunOutputsTest {

    @Autowired JdbcClient jdbc;
    JdbcAgentRunOutputs outputs;

    @BeforeEach
    void setUp() {
        outputs = new JdbcAgentRunOutputs(jdbc);
        jdbc.sql("INSERT INTO agent_runs (run_id, status, started_at) VALUES ('run-1', 'RUNNING', ?)")
                .params(OffsetDateTime.now()).update();
    }

    @Test
    void storesFullOutputsInExecutionOrderAndReplacesOnReRun() {
        String longOutput = "x".repeat(50_000);
        outputs.record("run-1", "a2a:litellm:qa-engineer", 1, "verified");
        outputs.record("run-1", "a2a:litellm:business-analyst", 0, "draft");
        outputs.record("run-1", "a2a:litellm:business-analyst", 0, longOutput);

        assertThat(outputs.find("run-1")).extracting(AgentRunOutputs.Output::agentId)
                .containsExactly("a2a:litellm:business-analyst", "a2a:litellm:qa-engineer");
        assertThat(outputs.find("run-1").getFirst().output()).hasSize(50_000);
        assertThat(outputs.find("other")).isEmpty();
    }

    @Test
    void outputsAreDeletedWithTheirRun() {
        outputs.record("run-1", "local:triage", 0, "ok");
        jdbc.sql("DELETE FROM agent_runs WHERE run_id = 'run-1'").update();
        assertThat(outputs.find("run-1")).isEmpty();
    }
}
