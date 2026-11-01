package nl.metafactory.agents.approval;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Durable store of runs parked at an approval gate (table {@code agent_parked_runs}, V7). A row exists
 * exactly while its run is parked. Ownership of a parked run is decided here, not in memory: a row is
 * only ever removed by a {@code DELETE ... WHERE run_id AND iteration AND version} whose row count is
 * checked, so of any number of concurrent decisions — in this instance or another one over the same
 * database — exactly one sees {@code true} and may resume the run.
 */
@Component
public class ParkedRunRepository {

    static final String AWAITING_APPROVAL = "AWAITING_APPROVAL";
    static final String RUNNING = "RUNNING";

    /** One parked run: its JSON snapshot plus the (iteration, version) pair that identifies this park. */
    public record ParkedRun(String runId, int iteration, long version, String state) {
    }

    private final JdbcClient jdbc;

    public ParkedRunRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Writes (or replaces) the parked row and sets the run's durable status to {@code AWAITING_APPROVAL}
     * in one transaction, so the startup reconciler never sees a parked run without its row or vice
     * versa. Returns the row's version.
     */
    @Transactional
    public long save(String runId, String gateStage, int iteration, String state) {
        var parkedAt = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        int replaced = jdbc.sql("""
                        UPDATE agent_parked_runs SET gate_stage = :stage, iteration = :iteration, state = :state,
                               parked_at = :at, version = version + 1
                        WHERE run_id = :run""")
                .param("stage", gateStage).param("iteration", iteration).param("state", state)
                .param("at", parkedAt).param("run", runId).update();
        if (replaced == 0) {
            jdbc.sql("""
                            INSERT INTO agent_parked_runs (run_id, gate_stage, iteration, state, parked_at, version)
                            VALUES (:run, :stage, :iteration, :state, :at, 0)""")
                    .param("run", runId).param("stage", gateStage).param("iteration", iteration)
                    .param("state", state).param("at", parkedAt).update();
        }
        jdbc.sql("UPDATE agent_runs SET status = :status WHERE run_id = :run")
                .param("status", AWAITING_APPROVAL).param("run", runId).update();
        return jdbc.sql("SELECT version FROM agent_parked_runs WHERE run_id = :run")
                .param("run", runId).query(Long.class).single();
    }

    public Optional<ParkedRun> find(String runId) {
        return jdbc.sql("SELECT run_id, iteration, version, state FROM agent_parked_runs WHERE run_id = :run")
                .param("run", runId).query(ParkedRunRepository::row).optional();
    }

    public List<ParkedRun> findAll() {
        return jdbc.sql("SELECT run_id, iteration, version, state FROM agent_parked_runs ORDER BY parked_at, run_id")
                .query(ParkedRunRepository::row).list();
    }

    /**
     * Claims the parked run: deletes the row only if it is still exactly this park (same iteration and
     * version) and, when that succeeds, moves the durable status from {@code AWAITING_APPROVAL} back to
     * {@code RUNNING} in the same transaction. {@code true} for exactly one of any concurrent callers.
     */
    @Transactional
    public boolean claim(String runId, int iteration, long version) {
        int deleted = jdbc.sql("DELETE FROM agent_parked_runs WHERE run_id = :run AND iteration = :iteration AND version = :version")
                .param("run", runId).param("iteration", iteration).param("version", version).update();
        if (deleted == 1) {
            jdbc.sql("UPDATE agent_runs SET status = :running WHERE run_id = :run AND status = :awaiting")
                    .param("running", RUNNING).param("run", runId).param("awaiting", AWAITING_APPROVAL).update();
        }
        return deleted == 1;
    }

    public int count() {
        return jdbc.sql("SELECT COUNT(*) FROM agent_parked_runs").query(Integer.class).single();
    }

    private static ParkedRun row(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ParkedRun(rs.getString(1), rs.getInt(2), rs.getLong(3), rs.getString(4));
    }
}
