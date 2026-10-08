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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles {@link AgentRunRecord} rows that were left in a non-terminal state because the
 * process that owned them died (e.g. a crash or forced shutdown) before the run could reach a
 * terminal status. Such rows are marked {@link AgentRunStatuses#RUN_STATE_LOST} and stamped with
 * {@code reconciledAt = now}.
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
public class AgentRunReconciliationService implements ApplicationRunner {

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
                .createQuery("UPDATE AgentRunRecord r SET r.status = :lostStatus, r.reconciledAt = :now WHERE r.status NOT IN :excludedStatuses")
                .setParameter("lostStatus", AgentRunStatuses.RUN_STATE_LOST)
                .setParameter("now", Instant.now())
                .setParameter("excludedStatuses", excludedStatuses)
                .executeUpdate();

        log.info("Reconciled {} interrupted execution(s) to RUN_STATE_LOST", count);
    }
}
