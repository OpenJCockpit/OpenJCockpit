package nl.metafactory.agents.workflowtrigger;

import nl.metafactory.agents.config.WorkflowTriggerProperties;
import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.orchestration.AgentRunStore;
import nl.metafactory.agents.orchestration.PipelineState;
import nl.metafactory.agents.orchestration.RunStatuses;
import nl.metafactory.agents.workflow.ChildStartDecision;
import nl.metafactory.agents.workflow.ChildWorkflowStarter;
import nl.metafactory.agents.workflow.WorkflowChainResolver;
import nl.metafactory.agents.workflow.WorkflowDefinitionRepository;
import nl.metafactory.agents.workflow.model.ExecutionConfig;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowOrb;
import nl.metafactory.agents.workflow.model.WorkflowOrbMode;
import nl.metafactory.agents.workflow.model.WorkflowStartResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkflowOrbRunnerTest {

    private WorkflowDefinitionRepository workflowDefinitionRepository;
    private WorkflowChainResolver workflowChainResolver;
    private WorkflowTriggerProperties workflowTriggerProperties;
    private ChildWaitRegistry childWaitRegistry;
    private DeadlineScheduler deadlineScheduler;
    private WorkflowTriggerCoordinator workflowTriggerCoordinator;
    private AgentRunStore agentRunStore;
    private ChildWorkflowStarter childWorkflowStarter;
    @SuppressWarnings("unchecked")
    private ObjectProvider<ChildWorkflowStarter> childWorkflowStarterProvider;
    private WorkflowOrbRunner runner;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        workflowDefinitionRepository = mock(WorkflowDefinitionRepository.class);
        workflowChainResolver = mock(WorkflowChainResolver.class);
        workflowTriggerProperties = new WorkflowTriggerProperties();
        childWaitRegistry = new ChildWaitRegistry();
        deadlineScheduler = mock(DeadlineScheduler.class);
        workflowTriggerCoordinator = mock(WorkflowTriggerCoordinator.class);
        agentRunStore = mock(AgentRunStore.class);
        childWorkflowStarter = mock(ChildWorkflowStarter.class);
        childWorkflowStarterProvider = mock(ObjectProvider.class);
        when(childWorkflowStarterProvider.getObject()).thenReturn(childWorkflowStarter);
        runner = new WorkflowOrbRunner(workflowDefinitionRepository, workflowChainResolver, workflowTriggerProperties,
                childWaitRegistry, deadlineScheduler, workflowTriggerCoordinator, agentRunStore, childWorkflowStarterProvider);
    }

    private PipelineState samplePipelineState(String runId, String requestedBy, List<String> chainAncestry) {
        var request = new AgentRunRequest("cust1", "spec.md", List.of("requirement"), requestedBy,
                "https://github.com/org/repo", null, null, null, null, null, null,
                RunInitiator.trigger("test"), chainAncestry);
        return new PipelineState(runId, new SpecContent(runId, "spec.md", "spec.md", ""),
                Set.of("requirement"), request);
    }

    private WorkflowDefinition parentWithOrbs(String id, List<WorkflowOrb> orbs) {
        return new WorkflowDefinition(id, "Parent-" + id, "proj", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null,
                new ExecutionConfig("cust1", "https://github.com/org/repo", 300),
                false, null, "ACTIVE", null, null, null, orbs);
    }

    private WorkflowDefinition target(String id) {
        return new WorkflowDefinition(id, "Target-" + id, "proj", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null,
                new ExecutionConfig("cust1", "https://github.com/org/repo", 300),
                false, null, "ACTIVE", null, null, null, null);
    }

    @Test
    void returnsFalseWhenTheParentWasNotStartedFromAWorkflow() {
        var state = samplePipelineState("run-1", "dashboard-button", List.of());

        boolean result = runner.runOrbsAnchoredTo(state, null);

        assertThat(result).isFalse();
        verifyNoInteractions(workflowDefinitionRepository);
    }

    @Test
    void returnsFalseWhenTheParentWorkflowHasNoOrbs() {
        var state = samplePipelineState("run-1", "workflow:wf-1", List.of("wf-1"));
        when(workflowDefinitionRepository.findById("wf-1")).thenReturn(Optional.of(parentWithOrbs("wf-1", null)));

        boolean result = runner.runOrbsAnchoredTo(state, null);

        assertThat(result).isFalse();
    }

    @Test
    void sequentialOrbRefusalBlocksTheParentAndReturnsTrue() {
        var orb = new WorkflowOrb("wf-child", WorkflowOrbMode.SEQUENTIAL, null);
        var state = samplePipelineState("run-1", "workflow:wf-1", List.of("wf-1"));
        var parent = parentWithOrbs("wf-1", List.of(orb));
        when(workflowDefinitionRepository.findById("wf-1")).thenReturn(Optional.of(parent));
        when(workflowChainResolver.resolve("wf-child", parent, List.of("wf-1")))
                .thenReturn(new ChildStartDecision.Refused("CYCLE", "would create a cycle"));

        boolean result = runner.runOrbsAnchoredTo(state, null);

        assertThat(result).isTrue();
        verify(agentRunStore).setStatus("run-1", "BLOCKED");
        verify(childWorkflowStarter, never()).startWorkflowFromOrb(any());
    }

    @Test
    void parallelOrbRefusalRecordsAnEventAndContinuesWithoutBlocking() {
        var orb = new WorkflowOrb("wf-child", WorkflowOrbMode.PARALLEL, null);
        var state = samplePipelineState("run-1", "workflow:wf-1", List.of("wf-1"));
        var parent = parentWithOrbs("wf-1", List.of(orb));
        when(workflowDefinitionRepository.findById("wf-1")).thenReturn(Optional.of(parent));
        when(workflowChainResolver.resolve("wf-child", parent, List.of("wf-1")))
                .thenReturn(new ChildStartDecision.Refused("UNKNOWN_WORKFLOW", "does not exist"));

        boolean result = runner.runOrbsAnchoredTo(state, null);

        assertThat(result).isFalse();
        verify(agentRunStore, never()).setStatus(any(), any());
    }

    @Test
    void sequentialPermittedOrbParksTheParentAndSchedulesADeadline() {
        var orb = new WorkflowOrb("wf-child", WorkflowOrbMode.SEQUENTIAL, "impact");
        var state = samplePipelineState("run-1", "workflow:wf-1", List.of("wf-1"));
        var parent = parentWithOrbs("wf-1", List.of(orb));
        var childTarget = target("wf-child");
        when(workflowDefinitionRepository.findById("wf-1")).thenReturn(Optional.of(parent));
        when(workflowChainResolver.resolve("wf-child", parent, List.of("wf-1")))
                .thenReturn(new ChildStartDecision.Permitted(childTarget));
        when(childWorkflowStarter.startWorkflowFromOrb(any()))
                .thenReturn(new WorkflowStartResponse("wf-child", "run-child", "RUNNING", Instant.now(), "started"));
        ScheduledFuture<?> deadline = mock(ScheduledFuture.class);
        org.mockito.Mockito.doReturn(deadline).when(deadlineScheduler).schedule(any(), any());

        boolean result = runner.runOrbsAnchoredTo(state, "impact");

        assertThat(result).isTrue();
        assertThat(state.orbDone("orb:0")).isTrue();
        assertThat(childWaitRegistry.size()).isEqualTo(1);
        verify(deadlineScheduler).schedule(any(), any());
        verify(agentRunStore).setStatus("run-1", RunStatuses.AWAITING_CHILD_WORKFLOW);

        var agentIdCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        var evidenceRefCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(agentRunStore).recordEvent(org.mockito.ArgumentMatchers.eq("run-1"), agentIdCaptor.capture(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), evidenceRefCaptor.capture());
        assertThat(agentIdCaptor.getValue()).isEqualTo("workflow-orb");
        assertThat(evidenceRefCaptor.getValue()).isEqualTo("run://run-child");
    }

    @Test
    void sequentialOrbSelfClaimsWhenChildAlreadyTerminalAfterParking() {
        var orb = new WorkflowOrb("wf-child", WorkflowOrbMode.SEQUENTIAL, "impact");
        var state = samplePipelineState("run-1", "workflow:wf-1", List.of("wf-1"));
        var parent = parentWithOrbs("wf-1", List.of(orb));
        var childTarget = target("wf-child");
        when(workflowDefinitionRepository.findById("wf-1")).thenReturn(Optional.of(parent));
        when(workflowChainResolver.resolve("wf-child", parent, List.of("wf-1")))
                .thenReturn(new ChildStartDecision.Permitted(childTarget));
        when(childWorkflowStarter.startWorkflowFromOrb(any()))
                .thenReturn(new WorkflowStartResponse("wf-child", "run-child", "RUNNING", Instant.now(), "started"));
        ScheduledFuture<?> deadline = mock(ScheduledFuture.class);
        org.mockito.Mockito.doReturn(deadline).when(deadlineScheduler).schedule(any(), any());
        when(agentRunStore.get("run-child")).thenReturn(new nl.metafactory.agents.model.AgentRun(
                "run-child", "cust1", "spec.md", "https://github.com/org/repo", "COMPLETED", Instant.now(),
                java.util.List.of(), java.util.List.of(), null, null, null, null));

        boolean result = runner.runOrbsAnchoredTo(state, "impact");

        assertThat(result).isTrue();
        assertThat(childWaitRegistry.size()).isEqualTo(1);
        verify(agentRunStore).setStatus("run-1", RunStatuses.AWAITING_CHILD_WORKFLOW);
        verify(workflowTriggerCoordinator).onRunTerminal(new RunTerminalEvent("run-child", "COMPLETED"));
    }

    @Test
    void statusIsSetToAwaitingChildWorkflowBeforeTheParentIsParked() {
        ChildWaitRegistry spiedRegistry = org.mockito.Mockito.spy(new ChildWaitRegistry());
        WorkflowOrbRunner localRunner = new WorkflowOrbRunner(workflowDefinitionRepository, workflowChainResolver,
                workflowTriggerProperties, spiedRegistry, deadlineScheduler, workflowTriggerCoordinator,
                agentRunStore, childWorkflowStarterProvider);
        var orb = new WorkflowOrb("wf-child", WorkflowOrbMode.SEQUENTIAL, "impact");
        var state = samplePipelineState("run-1", "workflow:wf-1", List.of("wf-1"));
        var parent = parentWithOrbs("wf-1", List.of(orb));
        var childTarget = target("wf-child");
        when(workflowDefinitionRepository.findById("wf-1")).thenReturn(Optional.of(parent));
        when(workflowChainResolver.resolve("wf-child", parent, List.of("wf-1")))
                .thenReturn(new ChildStartDecision.Permitted(childTarget));
        when(childWorkflowStarter.startWorkflowFromOrb(any()))
                .thenReturn(new WorkflowStartResponse("wf-child", "run-child", "RUNNING", Instant.now(), "started"));
        ScheduledFuture<?> deadline = mock(ScheduledFuture.class);
        org.mockito.Mockito.doReturn(deadline).when(deadlineScheduler).schedule(any(), any());

        boolean result = localRunner.runOrbsAnchoredTo(state, "impact");

        assertThat(result).isTrue();
        var inOrder = org.mockito.Mockito.inOrder(agentRunStore, spiedRegistry);
        inOrder.verify(agentRunStore).setStatus("run-1", RunStatuses.AWAITING_CHILD_WORKFLOW);
        inOrder.verify(spiedRegistry).park(org.mockito.ArgumentMatchers.eq("run-1"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void parallelPermittedOrbDoesNotParkAndReturnsFalse() {
        var orb = new WorkflowOrb("wf-child", WorkflowOrbMode.PARALLEL, null);
        var state = samplePipelineState("run-1", "workflow:wf-1", List.of("wf-1"));
        var parent = parentWithOrbs("wf-1", List.of(orb));
        var childTarget = target("wf-child");
        when(workflowDefinitionRepository.findById("wf-1")).thenReturn(Optional.of(parent));
        when(workflowChainResolver.resolve("wf-child", parent, List.of("wf-1")))
                .thenReturn(new ChildStartDecision.Permitted(childTarget));
        when(childWorkflowStarter.startWorkflowFromOrb(any()))
                .thenReturn(new WorkflowStartResponse("wf-child", "run-child", "RUNNING", Instant.now(), "started"));

        boolean result = runner.runOrbsAnchoredTo(state, null);

        assertThat(result).isFalse();
        assertThat(state.orbDone("orb:0")).isTrue();
        assertThat(childWaitRegistry.size()).isZero();
        verify(deadlineScheduler, never()).schedule(any(), any());
    }

    @Test
    void sequentialOrbWithNullExecutionIdBlocksTheParent() {
        var orb = new WorkflowOrb("wf-child", WorkflowOrbMode.SEQUENTIAL, null);
        var state = samplePipelineState("run-1", "workflow:wf-1", List.of("wf-1"));
        var parent = parentWithOrbs("wf-1", List.of(orb));
        var childTarget = target("wf-child");
        when(workflowDefinitionRepository.findById("wf-1")).thenReturn(Optional.of(parent));
        when(workflowChainResolver.resolve("wf-child", parent, List.of("wf-1")))
                .thenReturn(new ChildStartDecision.Permitted(childTarget));
        when(childWorkflowStarter.startWorkflowFromOrb(any()))
                .thenReturn(new WorkflowStartResponse("wf-child", null, "BLOCKED", Instant.now(), "denied by policy"));

        boolean result = runner.runOrbsAnchoredTo(state, null);

        assertThat(result).isTrue();
        verify(agentRunStore).setStatus("run-1", "BLOCKED");
        assertThat(childWaitRegistry.size()).isZero();
    }

    @Test
    void anAlreadyCompletedOrbIsSkipped() {
        var orb = new WorkflowOrb("wf-child", WorkflowOrbMode.SEQUENTIAL, null);
        var state = samplePipelineState("run-1", "workflow:wf-1", List.of("wf-1"));
        state.markOrbCompleted("orb:0");
        var parent = parentWithOrbs("wf-1", List.of(orb));
        when(workflowDefinitionRepository.findById("wf-1")).thenReturn(Optional.of(parent));

        boolean result = runner.runOrbsAnchoredTo(state, null);

        assertThat(result).isFalse();
        verifyNoInteractions(workflowChainResolver);
    }

    @Test
    void anOrbAnchoredToADifferentStageIsNotEvaluated() {
        var orb = new WorkflowOrb("wf-child", WorkflowOrbMode.SEQUENTIAL, "impact");
        var state = samplePipelineState("run-1", "workflow:wf-1", List.of("wf-1"));
        var parent = parentWithOrbs("wf-1", List.of(orb));
        when(workflowDefinitionRepository.findById("wf-1")).thenReturn(Optional.of(parent));

        boolean result = runner.runOrbsAnchoredTo(state, "requirement");

        assertThat(result).isFalse();
        verifyNoInteractions(workflowChainResolver);
    }

    @Test
    void disabledKillSwitchSkipsOrbsWithoutStartingAChildOrParkingTheParent() {
        var orb = new WorkflowOrb("wf-child", WorkflowOrbMode.SEQUENTIAL, null);
        var state = samplePipelineState("run-1", "workflow:wf-1", List.of("wf-1"));
        var parent = parentWithOrbs("wf-1", List.of(orb));
        when(workflowDefinitionRepository.findById("wf-1")).thenReturn(Optional.of(parent));
        workflowTriggerProperties.setEnabled(false);

        boolean result = runner.runOrbsAnchoredTo(state, null);

        assertThat(result).isFalse();
        assertThat(state.orbDone("orb:0")).isTrue();
        assertThat(childWaitRegistry.size()).isZero();
        verify(childWorkflowStarter, never()).startWorkflowFromOrb(any());
        verifyNoInteractions(workflowChainResolver);
        verify(agentRunStore).recordEvent(org.mockito.ArgumentMatchers.eq("run-1"), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("SKIPPED"), org.mockito.ArgumentMatchers.any());
    }
}
