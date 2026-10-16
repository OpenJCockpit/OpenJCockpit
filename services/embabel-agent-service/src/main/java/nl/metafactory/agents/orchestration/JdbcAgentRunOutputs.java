package nl.metafactory.agents.orchestration;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** PostgreSQL implementation of {@link AgentRunOutputs} (table {@code agent_run_outputs}, V5). */
@Component
public class JdbcAgentRunOutputs implements AgentRunOutputs {

    private final JdbcClient jdbc;

    public JdbcAgentRunOutputs(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void record(String runId, String agentId, int position, String output) {
        String text = output.length() > MAX_OUTPUT_CHARS ? output.substring(0, MAX_OUTPUT_CHARS) : output;
        jdbc.sql("DELETE FROM agent_run_outputs WHERE run_id = :run AND agent_id = :agent")
                .param("run", runId).param("agent", agentId).update();
        jdbc.sql("""
                        INSERT INTO agent_run_outputs (run_id, agent_id, position, output, recorded_at)
                        VALUES (:run, :agent, :position, :output, :at)""")
                .param("run", runId).param("agent", agentId).param("position", position).param("output", text)
                .param("at", OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC))
                .update();
    }

    @Override
    public List<Output> find(String runId) {
        return jdbc.sql("SELECT agent_id, position, output FROM agent_run_outputs WHERE run_id = :run ORDER BY position")
                .param("run", runId)
                .query((rs, n) -> new Output(rs.getString(1), rs.getInt(2), rs.getString(3)))
                .list();
    }
}
