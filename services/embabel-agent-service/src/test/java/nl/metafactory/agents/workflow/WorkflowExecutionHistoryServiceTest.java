package nl.metafactory.agents.workflow;

import nl.metafactory.agents.orchestration.AgentRunStore;
import nl.metafactory.agents.persistence.AgentRunPersistencePort;
import nl.metafactory.agents.persistence.InMemoryAgentRunPersistence;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowExecutionHistoryServiceTest {

    private WorkflowDefinitionRepository workflowRepository;
    private AgentRunStore runStore;
    private WorkflowExecutionHistoryService service;

    private static WorkflowDefinition workflow(String id) {
        return new WorkflowDefinition(id, "Name", "Project", null, "desc",
                List.of(), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
    }

    @BeforeEach
    void setUp() {
        workflowRepository = mock(WorkflowDefinitionRepository.class);
        runStore = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
        service = new WorkflowExecutionHistoryService(workflowRepository, runStore);
    }

    @Test
    void listThrowsNotFoundWhenWorkflowDoesNotExist() {
        when(workflowRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list("missing", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessageContaining("Workflow not found: missing");
    }

    @Test
    void listReturnsEmptyPageWhenWorkflowExistsButHasNoRuns() {
        when(workflowRepository.findById("wf-1")).thenReturn(Optional.of(workflow("wf-1")));

        var page = service.list("wf-1", null, null);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(page.limit()).isEqualTo(20);
        assertThat(page.offset()).isZero();
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void listMapsRunsToSummaryProjectionExcludingEventsAndArtifacts() {
        when(workflowRepository.findById("wf-1")).thenReturn(Optional.of(workflow("wf-1")));
        runStore.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");
        runStore.setStatus("run-1", "COMPLETED");

        var page = service.list("wf-1", null, null);

        assertThat(page.items()).hasSize(1);
        var summary = page.items().get(0);
        assertThat(summary.runId()).isEqualTo("run-1");
        assertThat(summary.workflowId()).isEqualTo("wf-1");
        assertThat(summary.status()).isEqualTo("COMPLETED");
        assertThat(summary.startedAt()).isNotNull();
        assertThat(summary.completedAt()).isNotNull();
        assertThat(summary.durationMillis()).isNotNull();
        assertThat(summary.startedBy()).isEqualTo("alice");
    }

    @Test
    void listNeverPopulatesDurationMillisForAStillRunningExecution() {
        when(workflowRepository.findById("wf-1")).thenReturn(Optional.of(workflow("wf-1")));
        runStore.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");

        var page = service.list("wf-1", null, null);

        assertThat(page.items().get(0).completedAt()).isNull();
        assertThat(page.items().get(0).durationMillis()).isNull();
    }

    @Test
    void listClampsNullLimitAndOffsetToDefaults() {
        when(workflowRepository.findById("wf-1")).thenReturn(Optional.of(workflow("wf-1")));

        var page = service.list("wf-1", null, null);

        assertThat(page.limit()).isEqualTo(20);
        assertThat(page.offset()).isZero();
    }

    @Test
    void listClampsOutOfRangeLimitAndNegativeOffset() {
        when(workflowRepository.findById("wf-1")).thenReturn(Optional.of(workflow("wf-1")));

        var page = service.list("wf-1", 1000, -5);

        assertThat(page.limit()).isEqualTo(100);
        assertThat(page.offset()).isZero();
    }

    @Test
    void detailThrowsNotFoundWhenWorkflowDoesNotExist() {
        when(workflowRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail("missing", "run-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Workflow not found: missing");
    }

    @Test
    void detailThrowsNotFoundForAnUnknownRunId() {
        when(workflowRepository.findById("wf-1")).thenReturn(Optional.of(workflow("wf-1")));

        assertThatThrownBy(() -> service.detail("wf-1", "unknown-run"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessageContaining("Execution not found");
    }

    @Test
    void detailThrowsNotFoundWhenRunBelongsToAnotherWorkflow() {
        when(workflowRepository.findById("wf-1")).thenReturn(Optional.of(workflow("wf-1")));
        runStore.create("run-1", "cust1", "spec.md", "", "wf-2", "alice");

        assertThatThrownBy(() -> service.detail("wf-1", "run-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Execution not found");
    }

    @Test
    void detailThrowsNotFoundWhenRunHasNullWorkflowId() {
        when(workflowRepository.findById("wf-1")).thenReturn(Optional.of(workflow("wf-1")));
        runStore.create("run-1", "cust1", "spec.md", "", null, "alice");

        assertThatThrownBy(() -> service.detail("wf-1", "run-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Execution not found");
    }

    @Test
    void detailReturnsTheFullAgentRunWhenWorkflowMatches() {
        when(workflowRepository.findById("wf-1")).thenReturn(Optional.of(workflow("wf-1")));
        runStore.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");

        var run = service.detail("wf-1", "run-1");

        assertThat(run.runId()).isEqualTo("run-1");
        assertThat(run.workflowId()).isEqualTo("wf-1");
    }

    @Test
    void detailNeverProducesARunStateLostShapedResponseForAnUnknownRun() {
        when(workflowRepository.findById("wf-1")).thenReturn(Optional.of(workflow("wf-1")));

        // A RUN_STATE_LOST fabrication (EmbabelOrchestrator.get's behaviour for unknown ids) would
        // return a 200 with a synthetic run body instead of throwing — proving this throws
        // confirms detail() never delegates to EmbabelOrchestrator.get(...).
        assertThatThrownBy(() -> service.detail("wf-1", "unknown-run"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void identicalNotFoundBodyForUnknownRunCrossWorkflowRunAndNullWorkflowIdRun() {
        when(workflowRepository.findById("wf-1")).thenReturn(Optional.of(workflow("wf-1")));
        runStore.create("run-cross", "cust1", "spec.md", "", "wf-2", "alice");
        runStore.create("run-null", "cust1", "spec.md", "", null, "alice");

        String unknownMessage = catchMessage(() -> service.detail("wf-1", "run-does-not-exist"));
        String crossWorkflowMessage = catchMessage(() -> service.detail("wf-1", "run-cross"));
        String nullWorkflowMessage = catchMessage(() -> service.detail("wf-1", "run-null"));

        assertThat(unknownMessage).isEqualTo(crossWorkflowMessage).isEqualTo(nullWorkflowMessage);
    }

    @Test
    void detailThrowsNotFoundForADurableRehydratedRunBelongingToAnotherWorkflow() {
        AgentRunPersistencePort mockPersistence = mock(AgentRunPersistencePort.class);
        AgentRunStore durableStore = new AgentRunStore(mockPersistence, event -> { });
        WorkflowDefinitionRepository durableWorkflowRepository = mock(WorkflowDefinitionRepository.class);
        WorkflowExecutionHistoryService durableService =
                new WorkflowExecutionHistoryService(durableWorkflowRepository, durableStore);

        when(durableWorkflowRepository.findById("wf-1")).thenReturn(Optional.of(workflow("wf-1")));
        when(mockPersistence.findFullRun("run-1")).thenReturn(Optional.of(new AgentRunPersistencePort.FullRun(
                "run-1", "wf-2", "cust1", "spec.md", "https://example.invalid/repo.git",
                "COMPLETED", null, null, "alice", null, List.of(), List.of(), null)));

        String unknownMessage = catchMessage(() -> durableService.detail("wf-1", "unknown-run"));
        String durableCrossWorkflowMessage = catchMessage(() -> durableService.detail("wf-1", "run-1"));

        assertThatThrownBy(() -> durableService.detail("wf-1", "run-1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessageContaining("Execution not found");
        assertThat(durableCrossWorkflowMessage).isEqualTo(unknownMessage);
    }

    private static String catchMessage(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected a ResponseStatusException");
        } catch (ResponseStatusException e) {
            return e.getReason();
        }
    }

    @Test
    void listAndDetailSucceedWhenWorkflowDefinitionFileIsUnreadableButARunExists() {
        when(workflowRepository.findById("wf-1")).thenThrow(new DefinitionFileReadException("workflows/wf-1.yaml", "StreamReadException at line 1, column 1", new java.io.IOException("parse failed")));
        runStore.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");

        var page = service.list("wf-1", null, null);
        assertThat(page.items()).hasSize(1);

        var run = service.detail("wf-1", "run-1");
        assertThat(run.runId()).isEqualTo("run-1");
    }

    @Test
    void requireWorkflowStillThrowsNotFoundByteIdenticalMessageWhenWorkflowIsGenuinelyAbsent() {
        when(workflowRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list("missing", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessageContaining("Workflow not found: missing");
    }

    @Test
    void requireWorkflowDoesNotSwallowABareUncheckedIOExceptionFromADirectoryLevelFailure() {
        when(workflowRepository.findById("wf-broken-dir")).thenThrow(new java.io.UncheckedIOException("Failed to list definition directory 'workflows'", new java.io.IOException("permission denied")));

        assertThatThrownBy(() -> service.list("wf-broken-dir", null, null))
                .isInstanceOf(java.io.UncheckedIOException.class);
    }

    @Test
    void detailStillThrowsNotFoundForARunBelongingToAnotherWorkflowEvenWhenTheRequestedWorkflowFileIsUnreadable() {
        when(workflowRepository.findById("wf-1")).thenThrow(new DefinitionFileReadException("workflows/wf-1.yaml", "StreamReadException at line 1, column 1", new java.io.IOException("parse failed")));
        runStore.create("run-cross", "cust1", "spec.md", "", "wf-2", "alice");

        assertThatThrownBy(() -> service.detail("wf-1", "run-cross"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Execution not found");
    }
}
