package nl.metafactory.agents.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import jakarta.persistence.EntityManager;
import nl.metafactory.agents.model.AgentRunStatuses;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.context.annotation.Import;

@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
@Import(AgentRunReconciliationService.class)
class AgentRunReconciliationServiceTest {

    @Autowired
    private AgentRunRecordRepository agentRunRecordRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AgentRunReconciliationService reconciliationService;

    private void seedFourRows() {
        agentRunRecordRepository.save(new AgentRunRecord("run-open-running", "wf-1", null, null, null,
                "RUNNING", Instant.now(), null, "test-user", null, null));
        agentRunRecordRepository.save(new AgentRunRecord("run-open-awaiting", "wf-1", null, null, null,
                "AWAITING_APPROVAL", Instant.now(), null, "test-user", null, null));
        agentRunRecordRepository.save(new AgentRunRecord("run-terminal-completed", "wf-1", null, null, null,
                "COMPLETED", Instant.now().minusSeconds(100), Instant.now().minusSeconds(10), "test-user", null, null));
        agentRunRecordRepository.save(new AgentRunRecord("run-already-lost", "wf-1", null, null, null,
                AgentRunStatuses.RUN_STATE_LOST, Instant.now().minusSeconds(200), null, "test-user", null, null));
        entityManager.flush();
    }

    @Test
    void reconciliationMarksOnlyOpenRowsAsLostAndIsIdempotent() {
        seedFourRows();
        entityManager.clear();
        Instant originalCompletedAt = agentRunRecordRepository.findById("run-terminal-completed")
                .orElseThrow().getCompletedAt();

        reconciliationService.run(null);
        entityManager.clear();

        var runningRow = agentRunRecordRepository.findById("run-open-running").orElseThrow();
        assertThat(runningRow.getStatus()).isEqualTo(AgentRunStatuses.RUN_STATE_LOST);
        assertThat(runningRow.getReconciledAt()).isNotNull();
        assertThat(runningRow.getCompletedAt()).isNull();

        var awaitingRow = agentRunRecordRepository.findById("run-open-awaiting").orElseThrow();
        assertThat(awaitingRow.getStatus()).isEqualTo(AgentRunStatuses.RUN_STATE_LOST);
        assertThat(awaitingRow.getReconciledAt()).isNotNull();
        assertThat(awaitingRow.getCompletedAt()).isNull();

        var terminalRow = agentRunRecordRepository.findById("run-terminal-completed").orElseThrow();
        assertThat(terminalRow.getStatus()).isEqualTo("COMPLETED");
        assertThat(terminalRow.getCompletedAt()).isEqualTo(originalCompletedAt);
        assertThat(terminalRow.getReconciledAt()).isNull();

        var alreadyLostRow = agentRunRecordRepository.findById("run-already-lost").orElseThrow();
        assertThat(alreadyLostRow.getStatus()).isEqualTo(AgentRunStatuses.RUN_STATE_LOST);
        assertThat(alreadyLostRow.getReconciledAt()).isNull();

        Instant runningReconciledAtFirstRun = runningRow.getReconciledAt();
        Instant awaitingReconciledAtFirstRun = awaitingRow.getReconciledAt();

        reconciliationService.run(null);
        entityManager.clear();

        var runningRowAfterSecondRun = agentRunRecordRepository.findById("run-open-running").orElseThrow();
        var awaitingRowAfterSecondRun = agentRunRecordRepository.findById("run-open-awaiting").orElseThrow();
        assertThat(runningRowAfterSecondRun.getReconciledAt()).isEqualTo(runningReconciledAtFirstRun);
        assertThat(awaitingRowAfterSecondRun.getReconciledAt()).isEqualTo(awaitingReconciledAtFirstRun);
    }

    @Test
    void reconciliationLogsOnlyACountNeverPerRowData() {
        seedFourRows();

        ch.qos.logback.classic.Logger logbackLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AgentRunReconciliationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
        try {
            reconciliationService.run(null);
        } finally {
            logbackLogger.detachAppender(appender);
        }

        assertThat(appender.list).hasSize(1);
        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message).contains("Reconciled");
        assertThat(message).containsPattern("\\d");
        assertThat(message).doesNotContain("test-user");
        assertThat(message).doesNotContain("run-open-running");
        assertThat(message).doesNotContain("run-open-awaiting");
        assertThat(message).doesNotContain("run-terminal-completed");
        assertThat(message).doesNotContain("run-already-lost");
        assertThat(message).doesNotContain("wf-1");
    }
}
