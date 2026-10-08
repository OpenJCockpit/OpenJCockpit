package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.agent.EvidenceAgent;
import nl.metafactory.agents.agent.ImpactAnalysisAgent;
import nl.metafactory.agents.agent.ImplementationAgent;
import nl.metafactory.agents.agent.RequirementAgent;
import nl.metafactory.agents.agent.ReviewAgent;
import nl.metafactory.agents.agent.TestDesignAgent;
import nl.metafactory.agents.approval.ApprovalDecisionAuditRepository;
import nl.metafactory.agents.approval.ApprovalDecisionService;
import nl.metafactory.agents.approval.ApprovalGateCoordinator;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.approval.model.ApprovalDecisionCommand;
import nl.metafactory.agents.approval.model.ApprovalDecisionKind;
import nl.metafactory.agents.approval.model.ApprovalGateConfig;
import nl.metafactory.agents.config.AgentPipelineProperties;
import nl.metafactory.agents.config.ApprovalGateProperties;
import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.domain.EvidenceBundle;
import nl.metafactory.agents.domain.ImpactReport;
import nl.metafactory.agents.domain.ImplementationPlan;
import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.ReviewReport;
import nl.metafactory.agents.domain.TestPlan;
import nl.metafactory.agents.approval.StageChangeReports;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.persistence.InMemoryAgentRunPersistence;
import nl.metafactory.agents.spec.CodeRealisationService;
import nl.metafactory.agents.spec.RealisationIteration;
import nl.metafactory.agents.spec.SpecGitPublisher;
import nl.metafactory.agents.spec.SpecPublication;
import nl.metafactory.agents.workflow.WorkflowDefinitionRepository;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.YamlDefinitionStore;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.time.Instant;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * End-to-end pause/resume through the real {@link EmbabelOrchestrator}, {@link ApprovalGateCoordinator},
 * {@link ApprovalGateRegistry} and {@link ApprovalDecisionService} — only the domain agents and
 * {@link CodeRealisationService} are mocked. {@link AsyncPipelineRunner} is used directly,
 * unmodified: outside a Spring proxy its {@code @Async} annotation has no effect, so calling it
 * directly is already synchronous — no sleeps, no waiting seam (architecture §14).
 */
class EmbabelOrchestratorPauseResumeTest {

    private RequirementAgent requirementAgent;
    private ImpactAnalysisAgent impactAnalysisAgent;
    private TestDesignAgent testDesignAgent;
    private ImplementationAgent implementationAgent;
    private ReviewAgent reviewAgent;
    private EvidenceAgent evidenceAgent;
    private CodeRealisationService codeRealisationService;
    private AgentRunStore runStore;
    private ApprovalGateRegistry registry;
    private ApprovalDecisionAuditRepository auditRepository;
    private EmbabelOrchestrator orchestrator;
    private ApprovalDecisionService decisionService;

    private final RequirementAnalysis ra = new RequirementAnalysis("id", List.of("req"), "summary");
    private final ImpactReport ir = new ImpactReport("id", List.of("m"), "LOW");
    private final TestPlan tp = new TestPlan("id", List.of("t"), "90%");
    private final ImplementationPlan ip = new ImplementationPlan("id", List.of("c"), "arch");
    private final ReviewReport rr = new ReviewReport("id", true, List.of());

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        requirementAgent = mock(RequirementAgent.class);
        impactAnalysisAgent = mock(ImpactAnalysisAgent.class);
        testDesignAgent = mock(TestDesignAgent.class);
        implementationAgent = mock(ImplementationAgent.class);
        reviewAgent = mock(ReviewAgent.class);
        evidenceAgent = mock(EvidenceAgent.class);
        codeRealisationService = mock(CodeRealisationService.class);
        var specGitPublisher = mock(SpecGitPublisher.class);

        when(impactAnalysisAgent.analyzeImpact(any(), any())).thenReturn(ir);
        when(testDesignAgent.designTests(any())).thenReturn(tp);
        when(implementationAgent.plan(any(), any())).thenReturn(ip);
        when(reviewAgent.review(any(), any())).thenReturn(rr);
        when(specGitPublisher.publishImplementation(any(), any(), any(), any(), any()))
                .thenReturn(SpecPublication.published("impl/wf-1-run", "https://github.com/org/repo/pull/7",
                        "Implementation plan pushed"));
        when(evidenceAgent.compileEvidence(any(), any(), any(), any(), any()))
                .thenReturn(new EvidenceBundle("id", List.of(), Instant.now()));

        var props = new AgentPipelineProperties();
        props.setSequence(List.of(
                "requirement", "impact", "test-design", "implementation", "review", "realisation", "evidence"));

        runStore = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
        registry = new ApprovalGateRegistry();
        var gateProperties = new ApprovalGateProperties();
        var gateCoordinator = new ApprovalGateCoordinator(registry, runStore, gateProperties);

        orchestrator = new EmbabelOrchestrator(requirementAgent, impactAnalysisAgent, testDesignAgent,
                implementationAgent, reviewAgent, evidenceAgent, props, new AsyncPipelineRunner(),
                specGitPublisher, codeRealisationService, runStore, gateCoordinator, registry,
                mock(nl.metafactory.agents.workflowtrigger.WorkflowOrbRunner.class),
                mock(nl.metafactory.agents.workflowtrigger.WorkflowTriggerCoordinator.class));

        var workflowProps = new WorkflowDefinitionProperties();
        workflowProps.setPath(tempDir.toString());
        auditRepository = new ApprovalDecisionAuditRepository(workflowProps, new YamlDefinitionStore());
        decisionService = new ApprovalDecisionService(registry, runStore, auditRepository, gateProperties, orchestrator);
    }

    private AgentRunRequest requestWithGateAfter(String placementStage) {
        return new AgentRunRequest("cust1", "file.md",
                List.of("impact", "test-design", "implementation", "review", "evidence"),
                "user", "", null, null, new ApprovalGateConfig(true, placementStage), null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
    }

    @Test
    void gateOpensAfterTheConfiguredStageAndNoSubsequentStageRuns() {
        var run = orchestrator.start(requestWithGateAfter("review"));

        var result = orchestrator.get(run.runId());
        assertThat(result.status()).isEqualTo("AWAITING_APPROVAL");
        verifyNoInteractions(evidenceAgent);
        assertThat(registry.isParked(run.runId())).isTrue();
    }

    @Test
    void acceptResumesAndCompletesTheRemainingStages() {
        var run = orchestrator.start(requestWithGateAfter("review"));
        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("AWAITING_APPROVAL");

        decisionService.submit(run.runId(), new ApprovalDecisionCommand(ApprovalDecisionKind.ACCEPT, 1,
                null, "alice", "sub-1"));

        var result = orchestrator.get(run.runId());
        assertThat(result.status()).isEqualTo("COMPLETED");
        verify(evidenceAgent).compileEvidence(any(), any(), any(), any(), any());
        assertThat(registry.isParked(run.runId())).isFalse();
    }

    @Test
    void completedStagesAreNotReExecutedAfterResume() {
        var run = orchestrator.start(requestWithGateAfter("review"));
        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("AWAITING_APPROVAL");

        decisionService.submit(run.runId(), new ApprovalDecisionCommand(ApprovalDecisionKind.ACCEPT, 1,
                null, "alice", "sub-1"));

        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("COMPLETED");
        verify(impactAnalysisAgent, org.mockito.Mockito.times(1)).analyzeImpact(any(), any());
        verify(testDesignAgent, org.mockito.Mockito.times(1)).designTests(any());
        verify(implementationAgent, org.mockito.Mockito.times(1)).plan(any(), any());
        verify(reviewAgent, org.mockito.Mockito.times(1)).review(any(), any());
    }

    @Test
    void stagesCompletedBeforeAnOrbParkAreNotReExecutedAfterTheChildWorkflowCompletes() {
        var specGitPublisherForOrb = mock(SpecGitPublisher.class);
        var props = new AgentPipelineProperties();
        props.setSequence(List.of(
                "requirement", "impact", "test-design", "implementation", "review", "realisation", "evidence"));
        var gateProperties = new ApprovalGateProperties();
        var gateCoordinatorForOrb = new ApprovalGateCoordinator(registry, runStore, gateProperties);

        var workflowDefinitionRepository = mock(WorkflowDefinitionRepository.class);
        var workflowChainResolver = mock(nl.metafactory.agents.workflow.WorkflowChainResolver.class);
        var workflowTriggerProperties = new nl.metafactory.agents.config.WorkflowTriggerProperties();
        var childWaitRegistry = new nl.metafactory.agents.workflowtrigger.ChildWaitRegistry();
        var deadlineScheduler = mock(nl.metafactory.agents.workflowtrigger.DeadlineScheduler.class);
        var childWorkflowStarter = mock(nl.metafactory.agents.workflow.ChildWorkflowStarter.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<nl.metafactory.agents.workflow.ChildWorkflowStarter> childWorkflowStarterProvider = mock(ObjectProvider.class);
        when(childWorkflowStarterProvider.getObject()).thenReturn(childWorkflowStarter);

        @SuppressWarnings("unchecked")
        ObjectProvider<PipelineContinuation> pipelineContinuationProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AgentOrchestrator> agentOrchestratorProvider = mock(ObjectProvider.class);
        var workflowTriggerCoordinator = new nl.metafactory.agents.workflowtrigger.WorkflowTriggerCoordinator(
                childWaitRegistry, runStore, pipelineContinuationProvider, agentOrchestratorProvider);

        var workflowOrbRunner = new nl.metafactory.agents.workflowtrigger.WorkflowOrbRunner(
                workflowDefinitionRepository, workflowChainResolver, workflowTriggerProperties, childWaitRegistry,
                deadlineScheduler, workflowTriggerCoordinator, runStore, childWorkflowStarterProvider);

        var orchestratorWithOrbs = new EmbabelOrchestrator(requirementAgent, impactAnalysisAgent, testDesignAgent,
                implementationAgent, reviewAgent, evidenceAgent, props, new AsyncPipelineRunner(),
                specGitPublisherForOrb, codeRealisationService, runStore, gateCoordinatorForOrb, registry,
                workflowOrbRunner, workflowTriggerCoordinator);
        when(pipelineContinuationProvider.getObject()).thenReturn(orchestratorWithOrbs);
        when(agentOrchestratorProvider.getObject()).thenReturn(orchestratorWithOrbs);

        var orb = new nl.metafactory.agents.workflow.model.WorkflowOrb("wf-child",
                nl.metafactory.agents.workflow.model.WorkflowOrbMode.SEQUENTIAL, "impact");
        var parentDefinition = new WorkflowDefinition("wf-1", "Parent", "proj", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null,
                new nl.metafactory.agents.workflow.model.ExecutionConfig("cust1", "https://github.com/org/repo", 300),
                false, null, "ACTIVE", null, null, null, List.of(orb));
        var childDefinition = new WorkflowDefinition("wf-child", "Child", "proj", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null,
                new nl.metafactory.agents.workflow.model.ExecutionConfig("cust1", "https://github.com/org/repo", 300),
                false, null, "ACTIVE", null, null, null, null);
        when(workflowDefinitionRepository.findById("wf-1")).thenReturn(Optional.of(parentDefinition));
        when(workflowChainResolver.resolve(org.mockito.ArgumentMatchers.eq("wf-child"),
                org.mockito.ArgumentMatchers.eq(parentDefinition), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new nl.metafactory.agents.workflow.ChildStartDecision.Permitted(childDefinition));
        when(childWorkflowStarter.startWorkflowFromOrb(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new nl.metafactory.agents.workflow.model.WorkflowStartResponse(
                        "wf-child", "run-child", "RUNNING", Instant.now(), "started"));
        java.util.concurrent.ScheduledFuture<?> deadline = mock(java.util.concurrent.ScheduledFuture.class);
        org.mockito.Mockito.doReturn(deadline).when(deadlineScheduler)
                .schedule(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        var request = new AgentRunRequest("cust1", "file.md", List.of("impact", "test-design"), "workflow:wf-1",
                "https://github.com/org/repo", null, null, null, null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), List.of("wf-1"));

        var run = orchestratorWithOrbs.start(request);

        assertThat(orchestratorWithOrbs.get(run.runId()).status()).isEqualTo(RunStatuses.AWAITING_CHILD_WORKFLOW);
        verify(impactAnalysisAgent, org.mockito.Mockito.times(1)).analyzeImpact(any(), any());
        verifyNoInteractions(testDesignAgent);

        workflowTriggerCoordinator.onRunTerminal(
                new nl.metafactory.agents.workflowtrigger.RunTerminalEvent("run-child", RunStatuses.COMPLETED));

        assertThat(orchestratorWithOrbs.get(run.runId()).status()).isEqualTo("COMPLETED");
        verify(impactAnalysisAgent, org.mockito.Mockito.times(1)).analyzeImpact(any(), any());
        verify(testDesignAgent, org.mockito.Mockito.times(1)).designTests(any());
    }

    @Test
    void denyStopsTheRunWithoutRunningRemainingStages() {
        var run = orchestrator.start(requestWithGateAfter("review"));

        decisionService.submit(run.runId(), new ApprovalDecisionCommand(ApprovalDecisionKind.DENY, 1,
                null, "alice", "sub-1"));

        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("DENIED");
        verifyNoInteractions(evidenceAgent);
    }

    @Test
    void postResumeFailureReachesFailedWithTheAuditTrailIntact() {
        when(evidenceAgent.compileEvidence(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("evidence agent broken"));
        var run = orchestrator.start(requestWithGateAfter("review"));

        decisionService.submit(run.runId(), new ApprovalDecisionCommand(ApprovalDecisionKind.ACCEPT, 1,
                null, "alice", "sub-1"));

        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("FAILED");
        assertThat(auditRepository.findByRunId(run.runId())).hasSize(1);
        assertThat(auditRepository.findByRunId(run.runId()).get(0).decision()).isEqualTo(ApprovalDecisionKind.ACCEPT);
    }

    @Test
    void stopOnAParkedRunActsImmediatelyBecauseThereIsNoThreadLeftToObserveCheckCancelled() {
        var run = orchestrator.start(requestWithGateAfter("review"));
        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("AWAITING_APPROVAL");

        orchestrator.stop(run.runId());

        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("CANCELLED");
        assertThat(registry.isParked(run.runId())).isFalse();
    }

    @Test
    void aDecisionSubmittedAfterStoppingAParkedRunIsRejected() {
        var run = orchestrator.start(requestWithGateAfter("review"));
        orchestrator.stop(run.runId());

        assertThatThrownBy(() -> decisionService.submit(run.runId(),
                new ApprovalDecisionCommand(ApprovalDecisionKind.ACCEPT, 1, null, "alice", "sub-1")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(e -> assertThat(((org.springframework.web.server.ResponseStatusException) e)
                        .getStatusCode().value()).isEqualTo(409));

        verifyNoInteractions(evidenceAgent);
    }

    // ── Batch B8: the full realisation loop-back, end to end ────────────────────

    @Test
    void acceptWithCommentsReRunsRealisationWithFeedbackAndReOpensAtIterationTwoThenAcceptCompletes() {
        var firstReport = StageChangeReports.published("feat/wf-1-run", "https://github.com/org/repo/pull/9",
                "initial summary", List.of("src/App.java"), "first realisation");
        var secondReport = StageChangeReports.published("feat/wf-1-run", "https://github.com/org/repo/pull/9",
                "revised summary", List.of("src/App.java", "src/Billing.java"), "second realisation");
        when(codeRealisationService.realise(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    RealisationIteration iteration = inv.getArgument(5);
                    return iteration.isFirst() ? firstReport : secondReport;
                });

        var request = new AgentRunRequest("cust1", "file.md", List.of("realisation", "evidence"), "user", "",
                null, null, new ApprovalGateConfig(true, "realisation"), null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var run = orchestrator.start(request);
        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("AWAITING_APPROVAL");

        decisionService.submit(run.runId(), new ApprovalDecisionCommand(
                ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 1, "please also update Billing.java", "alice", "sub-1"));

        // While the re-run is "in progress" (synchronous here), it has already produced the second
        // gate opening by the time submit() returns — AC-22/AC-23's essence without a sleep.
        var reOpened = orchestrator.get(run.runId());
        assertThat(reOpened.status()).isEqualTo("AWAITING_APPROVAL");

        var captor = org.mockito.ArgumentCaptor.forClass(RealisationIteration.class);
        verify(codeRealisationService, org.mockito.Mockito.times(2))
                .realise(any(), any(), any(), any(), any(), captor.capture());
        var secondCallIteration = captor.getAllValues().get(1);
        assertThat(secondCallIteration.number()).isEqualTo(2);
        assertThat(secondCallIteration.feedback()).isEqualTo("please also update Billing.java");
        assertThat(secondCallIteration.previousChangedPaths()).containsExactly("src/App.java");
        assertThat(secondCallIteration.retainedBranch()).isEqualTo("feat/wf-1-run");
        assertThat(secondCallIteration.retainedPullRequestUrl()).isEqualTo("https://github.com/org/repo/pull/9");

        decisionService.submit(run.runId(), new ApprovalDecisionCommand(ApprovalDecisionKind.ACCEPT, 2,
                null, "bob", "sub-2"));

        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("COMPLETED");
        verify(evidenceAgent).compileEvidence(any(), any(), any(), any(), any());
        var entries = auditRepository.findByRunId(run.runId());
        assertThat(entries).extracting(e -> e.decision())
                .containsExactly(ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, ApprovalDecisionKind.ACCEPT);
        assertThat(entries).extracting(e -> e.iteration()).containsExactly(1, 2);
    }

    @Test
    void denyAtIterationThreeAfterTwoFeedbackLoopsIsIdenticalToADenyAtIterationOne() {
        // AC-16/AC-17: deny at every iteration, including after feedback loops — no rollback.
        when(codeRealisationService.realise(any(), any(), any(), any(), any(), any()))
                .thenReturn(StageChangeReports.published("feat/wf-1-run", "https://github.com/org/repo/pull/9",
                        "summary", List.of("src/App.java"), "published"));
        var request = new AgentRunRequest("cust1", "file.md", List.of("realisation"), "user", "",
                null, null, new ApprovalGateConfig(true, "realisation"), null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var run = orchestrator.start(request);

        decisionService.submit(run.runId(), new ApprovalDecisionCommand(
                ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 1, "feedback one", "alice", "sub-1"));
        decisionService.submit(run.runId(), new ApprovalDecisionCommand(
                ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 2, "feedback two", "alice", "sub-1"));
        decisionService.submit(run.runId(), new ApprovalDecisionCommand(ApprovalDecisionKind.DENY, 3,
                null, "bob", "sub-2"));

        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("DENIED");
        var entries = auditRepository.findByRunId(run.runId());
        assertThat(entries).extracting(e -> e.decision()).containsExactly(
                ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, ApprovalDecisionKind.ACCEPT_WITH_COMMENTS,
                ApprovalDecisionKind.DENY);
        assertThat(entries).extracting(e -> e.iteration()).containsExactly(1, 2, 3);
    }

    @Test
    void feedbackBoundIsEnforcedAcrossRealResumes() {
        when(codeRealisationService.realise(any(), any(), any(), any(), any(), any()))
                .thenReturn(StageChangeReports.published("feat/wf-1-run", "https://github.com/org/repo/pull/9",
                        "summary", List.of("src/App.java"), "published"));
        var request = new AgentRunRequest("cust1", "file.md", List.of("realisation"), "user", "",
                null, null, new ApprovalGateConfig(true, "realisation"), null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var run = orchestrator.start(request);

        decisionService.submit(run.runId(), new ApprovalDecisionCommand(
                ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 1, "f1", "alice", "sub-1"));
        decisionService.submit(run.runId(), new ApprovalDecisionCommand(
                ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 2, "f2", "alice", "sub-1"));
        decisionService.submit(run.runId(), new ApprovalDecisionCommand(
                ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 3, "f3", "alice", "sub-1"));
        // Now at iteration 4 — the final, bound-exhausted review (BR-27/BR-28).

        assertThatThrownBy(() -> decisionService.submit(run.runId(), new ApprovalDecisionCommand(
                ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 4, "f4", "alice", "sub-1")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(e -> assertThat(((org.springframework.web.server.ResponseStatusException) e)
                        .getStatusCode().value()).isEqualTo(400));

        // The gate is still open at iteration 4 with Accept/Deny available — no LLM call, no re-run.
        verify(codeRealisationService, org.mockito.Mockito.times(4))
                .realise(any(), any(), any(), any(), any(), any());
        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("AWAITING_APPROVAL");

        decisionService.submit(run.runId(), new ApprovalDecisionCommand(ApprovalDecisionKind.DENY, 4,
                null, "bob", "sub-2"));
        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("DENIED");
    }

    @Test
    void anUnproductiveFeedbackIterationDoesNotFailTheRunAndStillReGates() {
        when(codeRealisationService.realise(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    RealisationIteration iteration = inv.getArgument(5);
                    return iteration.isFirst()
                            ? StageChangeReports.published("feat/wf-1-run", "https://github.com/org/repo/pull/9",
                                    "summary", List.of("src/App.java"), "published")
                            : StageChangeReports.noChange("feat/wf-1-run", "Realisation agent produced nothing new");
                });
        var request = new AgentRunRequest("cust1", "file.md", List.of("realisation"), "user", "",
                null, null, new ApprovalGateConfig(true, "realisation"), null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var run = orchestrator.start(request);

        decisionService.submit(run.runId(), new ApprovalDecisionCommand(
                ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 1, "please try again", "alice", "sub-1"));

        var result = orchestrator.get(run.runId());
        assertThat(result.status()).isEqualTo("AWAITING_APPROVAL");
        assertThat(result.status()).isNotEqualTo("FAILED");
    }

    @Test
    void stopOnARunThatIsNotParkedRemainsFireAndForgetAsBefore() {
        // A running (not-yet-gated) run's Stop is unaffected by the parked-run addition — it
        // still just flags cancellation for the pipeline's own checkCancelled to observe.
        var run = orchestrator.start(new AgentRunRequest("cust1", "file.md", List.of("impact"), "user", "",
                null, null, null, null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test")));

        orchestrator.stop(run.runId());

        // The pipeline already ran to completion synchronously (AsyncPipelineRunner is
        // effectively synchronous outside a Spring proxy) before stop() was called, so this just
        // proves stop() on an unparked, already-finished run does not throw or corrupt state.
        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("COMPLETED");
    }

    @Test
    void reconciliationMutatingTheRepositoryWhileParkedDoesNotAffectAnAlreadyParkedRun() {
        // INV-2: PipelineState's agent selection is captured once at start() and never re-read
        // from WorkflowDefinitionRepository, so a reconciler mutating the on-disk definition while
        // a run is parked cannot widen or narrow the stages that actually execute on resume.
        var repository = mock(WorkflowDefinitionRepository.class);
        var original = new WorkflowDefinition("wf-1", "Name", "Project", null, "desc",
                List.of("impact", "test-design", "implementation", "review"), List.of(), List.of(), List.of(),
                null, null, false, null, "ACTIVE", null, null, new ApprovalGateConfig(true, "review"), null);
        when(repository.findById("wf-1")).thenReturn(Optional.of(original));
        var request = new AgentRunRequest("cust1", "file.md",
                List.of("impact", "test-design", "implementation", "review"),
                "user", "", null, null, new ApprovalGateConfig(true, "review"), null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var run = orchestrator.start(request);
        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("AWAITING_APPROVAL");
        var mutated = original.withAgentIds(
                List.of("impact", "test-design", "implementation", "review", "evidence"));
        repository.save(mutated);
        decisionService.submit(run.runId(), new ApprovalDecisionCommand(ApprovalDecisionKind.ACCEPT, 1,
                null, "alice", "sub-1"));
        var result = orchestrator.get(run.runId());
        assertThat(result.status()).isEqualTo("COMPLETED");
        verifyNoInteractions(evidenceAgent);
        verify(repository).save(mutated);
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
