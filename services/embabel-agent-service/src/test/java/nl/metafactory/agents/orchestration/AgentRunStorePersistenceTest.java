package nl.metafactory.agents.orchestration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.model.AgentRunSummaryPage;
import nl.metafactory.agents.persistence.AgentRunPersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunStorePersistenceTest {

    private AgentRunPersistencePort port;
    private AgentRunStore store;

    @BeforeEach
    void setUp() {
        port = mock(AgentRunPersistencePort.class);
        store = new AgentRunStore(port, event -> { });
    }

    @Test
    void createFailurePropagatesAndDoesNotAddToMemory() {
        DataAccessException failure = new DataAccessException("db unavailable") {};
        doThrow(failure).when(port).recordNew(eq("run-1"), any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> store.create("run-1", "cust1", "spec.md", "", "wf-1", "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(rse.getReason()).isEqualTo("Workflow execution history is currently unavailable");
                });

        assertThat(store.get("run-1")).isNull();
    }

    @Test
    void createFailureOnCannotCreateTransactionExceptionPropagatesAndDoesNotAddToMemory() {
        CannotCreateTransactionException failure = new CannotCreateTransactionException("cannot create tx");
        doThrow(failure).when(port).recordNew(eq("run-1"), any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> store.create("run-1", "cust1", "spec.md", "", "wf-1", "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(rse.getReason()).isEqualTo("Workflow execution history is currently unavailable");
                });

        assertThat(store.get("run-1")).isNull();
    }

    @Test
    void appendEventFailureDoesNotPropagateAndMemoryStillAdvances() {
        store.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");
        doThrow(new RuntimeException("append boom")).when(port)
                .appendEvent(eq("run-1"), any(Integer.class), any(), any(), any(), any(), any());

        assertThatCode(() -> store.recordEvent("run-1", "agent-a", "did a thing", "OK", "evidence://x"))
                .doesNotThrowAnyException();

        assertThat(store.get("run-1").events()).hasSize(1);
        assertThat(store.get("run-1").events().get(0).title()).isEqualTo("did a thing");
    }

    @Test
    void appendArtifactFailureDoesNotPropagateAndMemoryStillAdvances() {
        store.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");
        doThrow(new RuntimeException("append boom")).when(port)
                .appendArtifact(eq("run-1"), any(Integer.class), any());

        assertThatCode(() -> store.addArtifact("run-1", "git-branch:feat/x"))
                .doesNotThrowAnyException();

        assertThat(store.get("run-1").generatedArtifacts()).containsExactly("git-branch:feat/x");
    }

    @Test
    void flushTerminalIsInvokedExactlyOnTerminalStatusTransitions() {
        store.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");

        store.setStatus("run-1", "AWAITING_APPROVAL");
        verify(port, never()).flushTerminal(any(), any(), any(), any(), any(), any());

        store.setStatus("run-1", "COMPLETED");
        verify(port, times(1)).flushTerminal(eq("run-1"), eq("COMPLETED"), any(), any(), any(), any());
    }

    @Test
    void flushTerminalFailureDoesNotPropagateAndMemoryStillReflectsTheTerminalTransition() {
        store.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");
        doThrow(new RuntimeException("flush boom")).when(port)
                .flushTerminal(eq("run-1"), any(), any(), any(), any(), any());

        assertThatCode(() -> store.setStatus("run-1", "COMPLETED"))
                .doesNotThrowAnyException();

        assertThat(store.get("run-1").status()).isEqualTo("COMPLETED");
        assertThat(store.get("run-1").completedAt()).isNotNull();
    }

    @Test
    void listByWorkflowOverlaysStaleDbSummaryWithFresherInMemoryStatus() {
        Instant startedAt = Instant.now().minusSeconds(60);
        AgentRunPersistencePort.PersistedRunSummary staleSummary = new AgentRunPersistencePort.PersistedRunSummary(
                "run-1", "wf-1", "RUNNING", startedAt, null, "alice");
        AgentRunPersistencePort.PersistedRunSummaryPage basePage =
                new AgentRunPersistencePort.PersistedRunSummaryPage(List.of(staleSummary), 1, false);
        when(port.listByWorkflow(eq("wf-1"), any(Integer.class), any(Integer.class))).thenReturn(basePage);

        store.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");
        store.setStatus("run-1", "COMPLETED");

        AgentRunSummaryPage page = store.listByWorkflow("wf-1", 20, 0);

        assertThat(page.items()).hasSize(1);
        var summary = page.items().get(0);
        assertThat(summary.runId()).isEqualTo("run-1");
        assertThat(summary.status()).isEqualTo("COMPLETED");
        assertThat(summary.completedAt()).isNotNull();
    }

    @Test
    void completedAtIsStampedExactlyOnceAndNeverOverwritten() {
        store.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");

        store.setStatus("run-1", "COMPLETED");
        Instant firstCompletedAt = store.get("run-1").completedAt();
        assertThat(firstCompletedAt).isNotNull();

        store.setStatus("run-1", "FAILED");
        Instant secondCompletedAt = store.get("run-1").completedAt();

        assertThat(secondCompletedAt).isEqualTo(firstCompletedAt);
        assertThat(store.get("run-1").status()).isEqualTo("FAILED");
    }

    @Test
    void completedAtIsNeverStampedOnTransitionToAwaitingApproval() {
        store.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");

        store.setStatus("run-1", "AWAITING_APPROVAL");

        assertThat(store.get("run-1").completedAt()).isNull();
        assertThat(store.get("run-1").status()).isEqualTo("AWAITING_APPROVAL");
    }

    @Test
    void findReturnsEmptyForATrulyUnknownIdAndNeverFabricates() {
        when(port.findFullRun("nonexistent-id")).thenReturn(Optional.empty());

        Optional<AgentRun> found = store.find("nonexistent-id");

        assertThat(found).isEmpty();
    }

    @Test
    void findRehydratesAFullRunFromDurableStorageWhenAbsentFromMemoryPreservingEventOrder() {
        Instant startedAt = Instant.now().minusSeconds(120);
        Instant completedAt = Instant.now().minusSeconds(30);
        Instant event0At = startedAt.plusSeconds(10);
        Instant event1At = startedAt.plusSeconds(20);

        List<AgentRunPersistencePort.EventSnapshot> events = List.of(
                new AgentRunPersistencePort.EventSnapshot(0, event0At, "agent-a", "step zero", "DONE", "evidence://0"),
                new AgentRunPersistencePort.EventSnapshot(1, event1At, "agent-b", "step one", "DONE", "evidence://1"));
        List<String> artifacts = List.of("artifact-0", "artifact-1");

        AgentRunPersistencePort.FullRun fullRun = new AgentRunPersistencePort.FullRun(
                "run-restored", "wf-1", "cust1", "spec.md", "https://example.invalid/repo.git",
                "COMPLETED", startedAt, completedAt, "alice", null, events, artifacts, "IllegalStateException");
        when(port.findFullRun("run-restored")).thenReturn(Optional.of(fullRun));

        Optional<AgentRun> found = store.find("run-restored");

        assertThat(found).isPresent();
        AgentRun run = found.get();
        assertThat(run.runId()).isEqualTo("run-restored");
        assertThat(run.workflowId()).isEqualTo("wf-1");
        assertThat(run.customerId()).isEqualTo("cust1");
        assertThat(run.status()).isEqualTo("COMPLETED");
        assertThat(run.startedAt()).isEqualTo(startedAt);
        assertThat(run.completedAt()).isEqualTo(completedAt);
        assertThat(run.startedBy()).isEqualTo("alice");
        assertThat(run.events()).hasSize(2);
        assertThat(run.events().get(0).title()).isEqualTo("step zero");
        assertThat(run.events().get(0).timestamp()).isEqualTo(event0At);
        assertThat(run.events().get(1).title()).isEqualTo("step one");
        assertThat(run.events().get(1).timestamp()).isEqualTo(event1At);
        assertThat(run.generatedArtifacts()).containsExactly("artifact-0", "artifact-1");
        assertThat(run.failureSummary()).isEqualTo("IllegalStateException");
    }

    @Test
    void secondSetStatusCallWithNoFailureSummaryDoesNotEraseAPreviouslyRecordedOne() {
        store.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");

        store.setStatus("run-1", "FAILED", "SomeException");
        assertThat(store.get("run-1").failureSummary()).isEqualTo("SomeException");

        store.setStatus("run-1", "CANCELLED");

        assertThat(store.get("run-1").failureSummary()).isEqualTo("SomeException");
    }

    @Test
    void failedTransitionWithFailureSummaryEmitsExactlyOneInfoLogWithSummaryLength() {
        Logger logger = (Logger) LoggerFactory.getLogger(AgentRunStore.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            store.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");

            store.setStatus("run-1", "FAILED", "SomeException");

            List<ILoggingEvent> infoEvents = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.INFO)
                    .toList();
            List<ILoggingEvent> failureRecordedEvents = infoEvents.stream()
                    .filter(e -> e.getFormattedMessage().contains("run.failure.recorded"))
                    .toList();

            assertThat(failureRecordedEvents).hasSize(1);
            ILoggingEvent event = failureRecordedEvents.get(0);
            assertThat(event.getFormattedMessage()).contains("run.failure.recorded");
            assertThat(event.getFormattedMessage()).contains("runId=run-1");
            assertThat(event.getFormattedMessage()).contains("status=FAILED");
            assertThat(event.getFormattedMessage()).contains("summaryLength=" + "SomeException".length());
            assertThat(event.getFormattedMessage()).doesNotContain("SomeException");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void failureSummaryIsNullForEveryNonFailedTerminalStatusViaTwoArgSetStatus() {
        String[][] cases = {
                {"run-completed", "COMPLETED"},
                {"run-cancelled", "CANCELLED"},
                {"run-timed-out", "TIMED_OUT"},
                {"run-denied", "DENIED"}
        };

        for (String[] c : cases) {
            String runId = c[0];
            String status = c[1];
            store.create(runId, "cust1", "spec.md", "", "wf-1", "alice");

            store.setStatus(runId, status);

            assertThat(store.get(runId).status()).isEqualTo(status);
            assertThat(store.get(runId).failureSummary()).isNull();
            verify(port).flushTerminal(eq(runId), eq(status), any(), isNull(), any(), any());
        }
    }

    @Test
    void failureSummaryIsNullForEveryNonFailedTerminalStatusViaThreeArgSetStatusWithNullSummary() {
        String[][] cases = {
                {"run-completed-3arg", "COMPLETED"},
                {"run-cancelled-3arg", "CANCELLED"},
                {"run-timed-out-3arg", "TIMED_OUT"},
                {"run-denied-3arg", "DENIED"}
        };

        for (String[] c : cases) {
            String runId = c[0];
            String status = c[1];
            store.create(runId, "cust1", "spec.md", "", "wf-1", "alice");

            store.setStatus(runId, status, null);

            assertThat(store.get(runId).status()).isEqualTo(status);
            assertThat(store.get(runId).failureSummary()).isNull();
            verify(port).flushTerminal(eq(runId), eq(status), any(), isNull(), any(), any());
        }
    }

    @Test
    void nonFailedTransitionDoesNotEmitFailureRecordedLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(AgentRunStore.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            store.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");

            store.setStatus("run-1", "COMPLETED");

            List<ILoggingEvent> failureRecordedEvents = appender.list.stream()
                    .filter(e -> e.getFormattedMessage().contains("run.failure.recorded"))
                    .toList();

            assertThat(failureRecordedEvents).isEmpty();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
