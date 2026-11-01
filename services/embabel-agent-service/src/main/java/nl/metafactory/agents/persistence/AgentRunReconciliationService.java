package nl.metafactory.agents.persistence;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import nl.metafactory.agents.model.AgentRunStatuses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles {@link AgentRunRecord} rows that were left in a non-terminal state because the
 * process that owned them died (e.g. a crash or forced shutdown) before the run could reach a
 * terminal status. Such rows are marked {@link AgentRunStatuses#RUN_STATE_LOST} and stamped with
 * {@code reconciledAt = now}.
 *
 * <p><b>Parked runs are not interrupted runs.</b> A run paused at an approval gate has a row in
 * {@code agent_parked_runs} (V7) holding everything needed to resume it; it is left alone here and
 * re-adopted by {@code ParkedRunRehydrator}, which runs after this service. Only runs that were
 * genuinely in flight — running, or waiting on something held in memory only, such as a child
 * workflow — i.e. non-terminal without a parked row, are marked lost.
 *
 * <p><b>Why {@link ApplicationRunner} and not {@code @PostConstruct} or
 * {@code ApplicationReadyEvent}:</b> {@code ApplicationRunner} beans run before the application's
 * readiness state flips to accepting traffic. This guarantees that an execution-list request can
 * never observe a stale "still running" row for a process that already died — the reconciliation
 * is complete before any client can read the data. {@code @PostConstruct} runs too early (before
 * the persistence context is fully wired in some configurations) and {@code ApplicationReadyEvent}
 * fires after readiness, leaving a window where a client could see the stale row.
 *
 * <p><b>Why {@code completedAt} is deliberately never set here:</b> we do not know when an
 * interrupted run actually stopped. Fabricating "now" as its end time would present false data as
 * real. {@code reconciledAt} records the true fact that reconciliation happened at time X, without
 * pretending to know a false fact about when the run ended.
 */
@Component
@Order(AgentRunReconciliationService.ORDER)
public class AgentRunReconciliationService implements ApplicationRunner {

    /** After the workflow importer (10), before {@code ParkedRunRehydrator} ({@code ORDER + 1}). */
    public static final int ORDER = 20;

    private static final Logger log = LoggerFactory.getLogger(AgentRunReconciliationService.class);

    private final EntityManager entityManager;

    public AgentRunReconciliationService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<String> excludedStatuses = new HashSet<>(AgentRunStatuses.TERMINAL_STATUSES);
        excludedStatuses.add(AgentRunStatuses.RUN_STATE_LOST);

        int count = entityManager
                .createNativeQuery("""
                        UPDATE agent_runs SET status = :lostStatus, reconciled_at = :now
                        WHERE status NOT IN (:excludedStatuses)
                          AND NOT EXISTS (SELECT 1 FROM agent_parked_runs p WHERE p.run_id = agent_runs.run_id)""")
                .setParameter("lostStatus", AgentRunStatuses.RUN_STATE_LOST)
                .setParameter("now", Instant.now())
                .setParameter("excludedStatuses", excludedStatuses)
                .executeUpdate();

        log.info("Reconciled {} interrupted execution(s) to RUN_STATE_LOST", count);
    }
}
