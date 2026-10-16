package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.approval.ApprovalDecisionAuditRepository;
import nl.metafactory.agents.approval.ApprovalDecisionService;
import nl.metafactory.agents.approval.ApprovalGateCoordinator;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.approval.model.ApprovalDecisionCommand;
import nl.metafactory.agents.approval.model.ApprovalDecisionKind;
import nl.metafactory.agents.approval.model.ApprovalGateConfig;
import nl.metafactory.agents.config.ApprovalGateProperties;
import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.config.WorkflowTriggerProperties;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.persistence.InMemoryAgentRunPersistence;
import nl.metafactory.agents.workflow.ChildStartDecision;
import nl.metafactory.agents.workflow.ChildWorkflowStarter;
import nl.metafactory.agents.workflow.WorkflowChainResolver;
import nl.metafactory.agents.workflow.WorkflowDefinitionRepository;
import nl.metafactory.agents.workflow.YamlDefinitionStore;
import nl.metafactory.agents.workflow.agents.AgentInvocationException;
import nl.metafactory.agents.workflow.agents.AgentRef;
import nl.metafactory.agents.workflow.agents.AgentWorkflowProperties;
import nl.metafactory.agents.workflow.agents.WorkflowAgentInvoker;
import nl.metafactory.agents.workflow.model.ExecutionConfig;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowOrb;
import nl.metafactory.agents.workflow.model.WorkflowOrbMode;
import nl.metafactory.agents.workflow.model.WorkflowStartResponse;
import nl.metafactory.agents.workflowtrigger.ChildWaitRegistry;
import nl.metafactory.agents.workflowtrigger.DeadlineScheduler;
import nl.metafactory.agents.workflowtrigger.RunTerminalEvent;
import nl.metafactory.agents.workflowtrigger.WorkflowOrbRunner;
import nl.metafactory.agents.workflowtrigger.WorkflowTriggerCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pause/resume of a workflow run, driven through the real {@link ApprovalGateCoordinator},
 * {@link ApprovalGateRegistry} and {@link ApprovalDecisionService} — only the
 * {@link WorkflowAgentInvoker} is mocked. {@link AsyncPipelineRunner} is used directly: outside a
 * Spring proxy its {@code @Async} annotation has no effect, so runs execute synchronously.
 */
class EmbabelOrchestratorPauseResumeTest {

    private static final String ANALYST = "local:analyst";
    private static final String ARCHITECT = "local:architect";
    private static final String DEVELOPER = "a2a:litellm:developer";
    private static final String QA = "a2a:litellm:qa";

    private WorkflowAgentInvoker invoker;
    private AgentRunStore runStore;
    private ApprovalGateRegistry registry;
    private ApprovalGateProperties gateProperties;
    private ApprovalDecisionAuditRepository auditRepository;
    private EmbabelOrchestrator orchestrator;
    private ApprovalDecisionService decisionService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        invoker = mock(WorkflowAgentInvoker.class);
        when(invoker.invoke(any(), anyString(), any())).thenAnswer(inv -> "output of " + inv.getArgument(0));

        runStore = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
        registry = new ApprovalGateRegistry();
        gateProperties = new ApprovalGateProperties();
        orchestrator = newOrchestrator(mock(WorkflowOrbRunner.class), mock(WorkflowTriggerCoordinator.class));

        var workflowProps = new WorkflowDefinitionProperties();
        workflowProps.setPath(tempDir.toString());
        auditRepository = new ApprovalDecisionAuditRepository(workflowProps, new YamlDefinitionStore());
        decisionService = new ApprovalDecisionService(registry, runStore, auditRepository, gateProperties, orchestrator);
    }

    private EmbabelOrchestrator newOrchestrator(WorkflowOrbRunner orbRunner, WorkflowTriggerCoordinator triggerCoordinator) {
        return new EmbabelOrchestrator(new AsyncPipelineRunner(), runStore,
                new ApprovalGateCoordinator(registry, runStore, gateProperties), registry,
                orbRunner, triggerCoordinator, invoker, new AgentWorkflowProperties(), new InMemoryAgentRunOutputs());
    }

    private static AgentRunRequest request(List<String> agentIds, ApprovalGateConfig gate) {
        return new AgentRunRequest("cust1", "the spec", agentIds, "user", "", null, null, gate,
                null, null, null, RunInitiator.trigger("test"), List.of("wf-test"));
    }

    private static AgentRunRequest requestWithGateAfter(String placement) {
        return request(List.of(ANALYST, ARCHITECT, DEVELOPER, QA), new ApprovalGateConfig(true, placement));
    }

    private static ApprovalDecisionCommand decision(ApprovalDecisionKind kind, int iteration, String comment) {
        return new ApprovalDecisionCommand(kind, iteration, comment, "alice", "sub-1");
    }

    private void verifyInvoked(String key, int count) {
        verify(invoker, times(count)).invoke(eq(AgentRef.parse(key).orElseThrow()), anyString(), any());
    }

    @Test
    void gateOpensAfterTheAnchoredAgentAndNoLaterAgentRuns() {
        var run = orchestrator.start(requestWithGateAfter(DEVELOPER));

        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("AWAITING_APPROVAL");
        assertThat(registry.isParked(run.runId())).isTrue();
        verifyInvoked(DEVELOPER, 1);
        verifyInvoked(QA, 0);
    }

    @Test
    void acceptResumesAndCompletesWithoutReExecutingCompletedAgents() {
        var run = orchestrator.start(requestWithGateAfter(DEVELOPER));

        decisionService.submit(run.runId(), decision(ApprovalDecisionKind.ACCEPT, 1, null));

        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("COMPLETED");
        assertThat(registry.isParked(run.runId())).isFalse();
        verifyInvoked(ANALYST, 1);
        verifyInvoked(ARCHITECT, 1);
        verifyInvoked(DEVELOPER, 1);
        verifyInvoked(QA, 1);
    }

    @Test
    void denyStopsTheRunWithoutRunningRemainingAgents() {
        var run = orchestrator.start(requestWithGateAfter(ARCHITECT));

        decisionService.submit(run.runId(), decision(ApprovalDecisionKind.DENY, 1, null));

        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("DENIED");
        verifyInvoked(DEVELOPER, 0);
        verifyInvoked(QA, 0);
    }

    @Test
    void postResumeFailureReachesFailedWithTheAuditTrailIntact() {
        when(invoker.invoke(eq(AgentRef.parse(QA).orElseThrow()), anyString(), any()))
                .thenThrow(new AgentInvocationException("qa agent unreachable"));
        var run = orchestrator.start(requestWithGateAfter(DEVELOPER));

        decisionService.submit(run.runId(), decision(ApprovalDecisionKind.ACCEPT, 1, null));

        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("FAILED");
        assertThat(auditRepository.findByRunId(run.runId())).hasSize(1);
        assertThat(auditRepository.findByRunId(run.runId()).get(0).decision()).isEqualTo(ApprovalDecisionKind.ACCEPT);
    }

    @Test
    void stopOnAParkedRunActsImmediatelyBecauseThereIsNoThreadLeftToObserveCheckCancelled() {
        var run = orchestrator.start(requestWithGateAfter(DEVELOPER));

        orchestrator.stop(run.runId());

        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("CANCELLED");
        assertThat(registry.isParked(run.runId())).isFalse();
    }

    @Test
    void aDecisionSubmittedAfterStoppingAParkedRunIsRejected() {
        var run = orchestrator.start(requestWithGateAfter(DEVELOPER));
        orchestrator.stop(run.runId());

        assertThatThrownBy(() -> decisionService.submit(run.runId(), decision(ApprovalDecisionKind.ACCEPT, 1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
        verifyInvoked(QA, 0);
    }

    @Test
    void acceptWithCommentsReRunsTheAnchoredAgentWithFeedbackThenAcceptCompletes() {
        var run = orchestrator.start(requestWithGateAfter(DEVELOPER));

        decisionService.submit(run.runId(), decision(ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 1, "add error handling"));

        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("AWAITING_APPROVAL");
        var messages = ArgumentCaptor.forClass(String.class);
        verify(invoker, times(2)).invoke(eq(AgentRef.parse(DEVELOPER).orElseThrow()), messages.capture(), any());
        assertThat(messages.getAllValues().get(0)).doesNotContain("Reviewer feedback");
        assertThat(messages.getAllValues().get(1))
                .contains("# Reviewer feedback on your previous output\nadd error handling")
                .contains("# Your previous output\noutput of " + DEVELOPER);
        verifyInvoked(ARCHITECT, 1);
        verifyInvoked(QA, 0);

        decisionService.submit(run.runId(), decision(ApprovalDecisionKind.ACCEPT, 2, null));

        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("COMPLETED");
        verifyInvoked(QA, 1);
    }

    @Test
    void feedbackBoundIsEnforcedAcrossRealResumes() {
        var run = orchestrator.start(request(List.of(DEVELOPER), new ApprovalGateConfig(true, DEVELOPER)));
        int max = gateProperties.getMaxFeedbackIterations();
        for (int iteration = 1; iteration <= max; iteration++) {
            decisionService.submit(run.runId(), decision(ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, iteration, "f" + iteration));
        }
        int finalIteration = max + 1;

        assertThatThrownBy(() -> decisionService.submit(run.runId(),
                decision(ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, finalIteration, "one more")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
        verifyInvoked(DEVELOPER, finalIteration);
        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("AWAITING_APPROVAL");

        decisionService.submit(run.runId(), new ApprovalDecisionCommand(ApprovalDecisionKind.DENY, finalIteration,
                null, "bob", "sub-2"));
        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("DENIED");
    }

    @Test
    void stopOnARunThatIsNotParkedRemainsFireAndForget() {
        var run = orchestrator.start(request(List.of(ANALYST), null));

        orchestrator.stop(run.runId());

        // The run already completed synchronously; stop() on a finished, unparked run is harmless.
        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("COMPLETED");
    }

    @Test
    void agentsCompletedBeforeAnOrbParkAreNotReExecutedAfterTheChildWorkflowCompletes() {
        var workflowDefinitionRepository = mock(WorkflowDefinitionRepository.class);
        var workflowChainResolver = mock(WorkflowChainResolver.class);
        var childWaitRegistry = new ChildWaitRegistry();
        var deadlineScheduler = mock(DeadlineScheduler.class);
        var childWorkflowStarter = mock(ChildWorkflowStarter.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChildWorkflowStarter> childWorkflowStarterProvider = mock(ObjectProvider.class);
        when(childWorkflowStarterProvider.getObject()).thenReturn(childWorkflowStarter);
        @SuppressWarnings("unchecked")
        ObjectProvider<PipelineContinuation> pipelineContinuationProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AgentOrchestrator> agentOrchestratorProvider = mock(ObjectProvider.class);
        var triggerCoordinator = new WorkflowTriggerCoordinator(
                childWaitRegistry, runStore, pipelineContinuationProvider, agentOrchestratorProvider);
        var orbRunner = new WorkflowOrbRunner(workflowDefinitionRepository, workflowChainResolver,
                new WorkflowTriggerProperties(), childWaitRegistry, deadlineScheduler, triggerCoordinator, runStore,
                childWorkflowStarterProvider);
        var orchestratorWithOrbs = newOrchestrator(orbRunner, triggerCoordinator);
        when(pipelineContinuationProvider.getObject()).thenReturn(orchestratorWithOrbs);
        when(agentOrchestratorProvider.getObject()).thenReturn(orchestratorWithOrbs);

        var execution = new ExecutionConfig("cust1", "https://github.com/org/repo", 300);
        var parent = new WorkflowDefinition("wf-1", "Parent", "proj", null, "desc", List.of(ANALYST, ARCHITECT),
                List.of(), List.of(), List.of(), null, execution, false, null, "ACTIVE", null, null, null,
                List.of(new WorkflowOrb("wf-child", WorkflowOrbMode.SEQUENTIAL, ANALYST)));
        var child = new WorkflowDefinition("wf-child", "Child", "proj", null, "desc", List.of(QA),
                List.of(), List.of(), List.of(), null, execution, false, null, "ACTIVE", null, null, null, null);
        when(workflowDefinitionRepository.findById("wf-1")).thenReturn(Optional.of(parent));
        when(workflowChainResolver.resolve(eq("wf-child"), eq(parent), any()))
                .thenReturn(new ChildStartDecision.Permitted(child));
        when(childWorkflowStarter.startWorkflowFromOrb(any()))
                .thenReturn(new WorkflowStartResponse("wf-child", "run-child", "RUNNING", Instant.now(), "started"));
        ScheduledFuture<?> deadline = mock(ScheduledFuture.class);
        doReturn(deadline).when(deadlineScheduler).schedule(any(), any());

        var run = orchestratorWithOrbs.start(new AgentRunRequest("cust1", "the spec", List.of(ANALYST, ARCHITECT),
                "workflow:wf-1", "https://github.com/org/repo", null, null, null, null, null, null,
                RunInitiator.trigger("test"), List.of("wf-1")));

        assertThat(orchestratorWithOrbs.get(run.runId()).status()).isEqualTo(RunStatuses.AWAITING_CHILD_WORKFLOW);
        verifyInvoked(ANALYST, 1);
        verifyInvoked(ARCHITECT, 0);

        triggerCoordinator.onRunTerminal(new RunTerminalEvent("run-child", RunStatuses.COMPLETED));

        assertThat(orchestratorWithOrbs.get(run.runId()).status()).isEqualTo("COMPLETED");
        verifyInvoked(ANALYST, 1);
        verifyInvoked(ARCHITECT, 1);
    }

    @Test
    void theAgentSelectionIsCapturedAtStartSoAParkedRunIgnoresLaterDefinitionChanges() {
        // INV-2: the orchestrator never reads WorkflowDefinitionRepository, so editing the workflow's
        // agent references while a run is parked cannot change what executes on resume.
        var run = orchestrator.start(request(List.of(ANALYST, DEVELOPER), new ApprovalGateConfig(true, ANALYST)));

        decisionService.submit(run.runId(), decision(ApprovalDecisionKind.ACCEPT, 1, null));

        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("COMPLETED");
        verify(invoker, never()).invoke(eq(AgentRef.parse(QA).orElseThrow()), anyString(), any());
    }

    @Test
    void constructorDeclaresNoWorkflowDefinitionRepositoryParameter() {
        Constructor<?>[] constructors = EmbabelOrchestrator.class.getDeclaredConstructors();
        boolean anyConstructorTakesWorkflowDefinitionRepository = Arrays.stream(constructors)
                .flatMap(c -> Arrays.stream(c.getParameterTypes()))
                .anyMatch(t -> t.getSimpleName().equals("WorkflowDefinitionRepository"));
        assertThat(anyConstructorTakesWorkflowDefinitionRepository).isFalse();
    }
}
