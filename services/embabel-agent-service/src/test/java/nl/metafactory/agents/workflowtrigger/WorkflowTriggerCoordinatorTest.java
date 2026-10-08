package nl.metafactory.agents.workflowtrigger;

import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import nl.metafactory.agents.orchestration.AgentRunStore;
import nl.metafactory.agents.orchestration.PipelineContinuation;
import nl.metafactory.agents.orchestration.PipelineState;
import nl.metafactory.agents.orchestration.RunStatuses;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class WorkflowTriggerCoordinatorTest {

    private final ChildWaitRegistry registry = new ChildWaitRegistry();
    private final AgentRunStore agentRunStore = mock(AgentRunStore.class);
    private final PipelineContinuation pipelineContinuation = mock(PipelineContinuation.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<PipelineContinuation> pipelineContinuationProvider = mock(ObjectProvider.class);
    private final AgentOrchestrator agentOrchestrator = mock(AgentOrchestrator.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AgentOrchestrator> agentOrchestratorProvider = mock(ObjectProvider.class);
    private final WorkflowTriggerCoordinator coordinator =
            new WorkflowTriggerCoordinator(registry, agentRunStore, pipelineContinuationProvider, agentOrchestratorProvider);

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(agentOrchestratorProvider.getObject()).thenReturn(agentOrchestrator);
        org.mockito.Mockito.lenient().when(pipelineContinuationProvider.getObject()).thenReturn(pipelineContinuation);
    }

    private PipelineState samplePipelineState(String runId) {
        var request = new AgentRunRequest("cust1", "spec.md", List.of("realisation"), "workflow:wf-1",
                "https://github.com/org/repo", null, null, null, null, null, null,
                RunInitiator.trigger("test"), List.of("wf-test"));
        return new PipelineState(runId, new SpecContent(runId, "spec.md", "spec.md", ""),
                Set.of("realisation"), request);
    }

    @SuppressWarnings("unchecked")
    private ChildWait sampleWait(String parentRunId, String childRunId) {
        ScheduledFuture<Object> deadline = mock(ScheduledFuture.class);
        return new ChildWait(samplePipelineState(parentRunId), childRunId, "wf-child", "Child Workflow",
                "impact", Instant.now(), deadline);
    }

    @Test
    void onRunTerminalWithCompletedChildResumesTheParent() {
        var wait = sampleWait("parent-1", "child-1");
        registry.park("parent-1", wait);

        coordinator.onRunTerminal(new RunTerminalEvent("child-1", RunStatuses.COMPLETED));

        verify(pipelineContinuation).resume(wait.parentState());
        verify(agentRunStore, never()).setStatus(anyString(), anyString());
    }

    @Test
    void onRunTerminalWithFailedChildFailsTheParentWithoutResuming() {
        var wait = sampleWait("parent-2", "child-2");
        registry.park("parent-2", wait);

        coordinator.onRunTerminal(new RunTerminalEvent("child-2", RunStatuses.FAILED));

        verify(agentRunStore).setStatus("parent-2", RunStatuses.FAILED);
        verify(pipelineContinuation, never()).resume(any());
    }

    @Test
    void onRunTerminalWithDeniedChildDeniesTheParent() {
        var wait = sampleWait("parent-3", "child-3");
        registry.park("parent-3", wait);

        coordinator.onRunTerminal(new RunTerminalEvent("child-3", RunStatuses.DENIED));

        verify(agentRunStore).setStatus("parent-3", RunStatuses.DENIED);
    }

    @Test
    void onRunTerminalWithRunStateLostChildPropagatesRunStateLostToTheParent() {
        var wait = sampleWait("parent-4", "child-4");
        registry.park("parent-4", wait);

        coordinator.onRunTerminal(new RunTerminalEvent("child-4", RunStatuses.RUN_STATE_LOST));

        verify(agentRunStore).setStatus("parent-4", RunStatuses.RUN_STATE_LOST);
    }

    @Test
    void onRunTerminalForAnUnparkedChildDoesNothing() {
        coordinator.onRunTerminal(new RunTerminalEvent("no-such-child", RunStatuses.COMPLETED));

        verifyNoInteractions(pipelineContinuation);
        verify(agentRunStore, never()).setStatus(anyString(), anyString());
    }

    @Test
    void onRunTerminalNeverPropagatesAnExceptionFromItsCollaborators() {
        var wait = sampleWait("parent-5", "child-5");
        registry.park("parent-5", wait);
        doThrow(new RuntimeException("boom")).when(agentRunStore).recordEvent(anyString(), anyString(), anyString(), anyString(), anyString());

        coordinator.onRunTerminal(new RunTerminalEvent("child-5", RunStatuses.COMPLETED));

        verify(pipelineContinuation, never()).resume(any());
    }

    @Test
    void onDeadlineExpiredSetsParentTimedOutWithoutStoppingTheChild() {
        var wait = sampleWait("parent-6", "child-6");
        registry.park("parent-6", wait);

        coordinator.onDeadlineExpired("parent-6");

        verify(agentRunStore).setStatus("parent-6", RunStatuses.TIMED_OUT);
        verify(agentOrchestrator, never()).stop(anyString());
    }

    @Test
    void onDeadlineExpiredForAnUnparkedParentDoesNothing() {
        coordinator.onDeadlineExpired("no-such-parent");

        verify(agentRunStore, never()).setStatus(anyString(), anyString());
    }

    @Test
    void onParentStoppedCascadesToTheChildAndCancelsTheParent() {
        var wait = sampleWait("parent-7", "child-7");
        registry.park("parent-7", wait);

        coordinator.onParentStopped("parent-7");

        verify(agentRunStore).setStatus("parent-7", "CANCELLED");
        verify(agentOrchestrator).stop("child-7");
    }

    @Test
    void onParentStoppedForAnUnparkedParentDoesNothing() {
        coordinator.onParentStopped("no-such-parent");

        verify(agentOrchestrator, never()).stop(anyString());
    }

    @Test
    void claimIsSingleOwnerAcrossAllThreeEntryPoints() {
        var wait = sampleWait("parent-8", "child-8");
        registry.park("parent-8", wait);

        coordinator.onDeadlineExpired("parent-8");
        coordinator.onParentStopped("parent-8");
        coordinator.onRunTerminal(new RunTerminalEvent("child-8", RunStatuses.COMPLETED));

        assertThat(registry.size()).isZero();
        verify(agentRunStore, org.mockito.Mockito.times(1)).setStatus("parent-8", RunStatuses.TIMED_OUT);
        verify(agentOrchestrator, never()).stop(anyString());
        verify(pipelineContinuation, never()).resume(any());
    }

    @Test
    void childOwnApprovalGateKeepsTheParentParkedUntilTheChildLaterCompletes() {
        nl.metafactory.agents.orchestration.AgentRunStore childRunStore =
                new nl.metafactory.agents.orchestration.AgentRunStore(new nl.metafactory.agents.persistence.InMemoryAgentRunPersistence(), event -> {
                    if (event instanceof RunTerminalEvent terminalEvent) {
                        coordinator.onRunTerminal(terminalEvent);
                    }
                });
        childRunStore.create("child-9", "cust1", "spec.md", "https://github.com/org/repo", null, null);

        var wait = sampleWait("parent-9", "child-9");
        registry.park("parent-9", wait);

        childRunStore.setStatus("child-9", "AWAITING_APPROVAL");
        assertThat(registry.size()).isEqualTo(1);
        verify(pipelineContinuation, never()).resume(any());

        childRunStore.setStatus("child-9", nl.metafactory.agents.orchestration.RunStatuses.COMPLETED);
        verify(pipelineContinuation).resume(wait.parentState());
        assertThat(registry.size()).isZero();
    }

    @Test
    void blockedStatusPropagatesThroughTwoLevelsOfParentChildWaiting() {
        nl.metafactory.agents.orchestration.AgentRunStore parentRunStore =
                new nl.metafactory.agents.orchestration.AgentRunStore(new nl.metafactory.agents.persistence.InMemoryAgentRunPersistence(), event -> {
                    if (event instanceof RunTerminalEvent terminalEvent) {
                        coordinator.onRunTerminal(terminalEvent);
                    }
                });
        parentRunStore.create("parent-blocked", "cust1", "spec.md", "https://github.com/org/repo", null, null);

        var wait = sampleWait("grandparent-1", "parent-blocked");
        registry.park("grandparent-1", wait);

        parentRunStore.setStatus("parent-blocked", RunStatuses.BLOCKED);

        assertThat(registry.size()).isZero();
        verify(agentRunStore).setStatus("grandparent-1", RunStatuses.BLOCKED);
        verify(agentRunStore, never()).setStatus("grandparent-1", RunStatuses.TIMED_OUT);
        verify(pipelineContinuation, never()).resume(any());
    }
}
