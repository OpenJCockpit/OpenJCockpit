package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.agent.EvidenceAgent;
import nl.metafactory.agents.approval.ApprovalGateCoordinator;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.approval.StageChangeReports;
import nl.metafactory.agents.agent.ImpactAnalysisAgent;
import nl.metafactory.agents.agent.ImplementationAgent;
import nl.metafactory.agents.agent.RequirementAgent;
import nl.metafactory.agents.agent.ReviewAgent;
import nl.metafactory.agents.agent.TestDesignAgent;
import nl.metafactory.agents.config.AgentPipelineProperties;
import nl.metafactory.agents.domain.EvidenceBundle;
import nl.metafactory.agents.domain.ImpactReport;
import nl.metafactory.agents.domain.ImplementationPlan;
import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.ReviewReport;
import nl.metafactory.agents.domain.TestPlan;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.persistence.InMemoryAgentRunPersistence;
import nl.metafactory.agents.spec.CodeRealisationService;
import nl.metafactory.agents.spec.SpecGitPublisher;
import nl.metafactory.agents.spec.SpecPublication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EmbabelOrchestratorTest {

    private RequirementAgent requirementAgent;
    private ImpactAnalysisAgent impactAnalysisAgent;
    private TestDesignAgent testDesignAgent;
    private ImplementationAgent implementationAgent;
    private ReviewAgent reviewAgent;
    private EvidenceAgent evidenceAgent;
    private AsyncPipelineRunner asyncPipelineRunner;
    private SpecGitPublisher specGitPublisher;
    private CodeRealisationService codeRealisationService;
    private AgentRunStore runStore;
    private ApprovalGateCoordinator gateCoordinator;
    private ApprovalGateRegistry gateRegistry;
    private EmbabelOrchestrator orchestrator;

    private final RequirementAnalysis ra = new RequirementAnalysis("id", List.of("req"), "summary");
    private final ImpactReport ir = new ImpactReport("id", List.of("m"), "LOW");
    private final TestPlan tp = new TestPlan("id", List.of("t"), "90%");
    private final ImplementationPlan ip = new ImplementationPlan("id", List.of("c"), "arch");
    private final ReviewReport rr = new ReviewReport("id", true, List.of());
    private final EvidenceBundle bundle = new EvidenceBundle("id", List.of(), Instant.now());

    @BeforeEach
    void setUp() {
        requirementAgent = mock(RequirementAgent.class);
        impactAnalysisAgent = mock(ImpactAnalysisAgent.class);
        testDesignAgent = mock(TestDesignAgent.class);
        implementationAgent = mock(ImplementationAgent.class);
        reviewAgent = mock(ReviewAgent.class);
        evidenceAgent = mock(EvidenceAgent.class);
        asyncPipelineRunner = mock(AsyncPipelineRunner.class);
        specGitPublisher = mock(SpecGitPublisher.class);
        when(specGitPublisher.publish(any(), any(), any()))
                .thenReturn(SpecPublication.published("spec/wf-1-run", "Spec pushed to branch spec/wf-1-run"));
        when(specGitPublisher.publishImplementation(any(), any(), any(), any(), any()))
                .thenReturn(SpecPublication.published("impl/wf-1-run", "https://github.com/org/repo/pull/7",
                        "Implementation plan pushed to branch impl/wf-1-run"));
        codeRealisationService = mock(CodeRealisationService.class);
        when(codeRealisationService.realise(any(), any(), any(), any(), any(), any()))
                .thenReturn(StageChangeReports.published("feat/wf-1-run", "https://github.com/org/repo/pull/9",
                        null, List.of(), "Implementation pushed to branch feat/wf-1-run"));

        // Run the pipeline synchronously in tests so assertions can check the final state.
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                .when(asyncPipelineRunner).run(any());

        var props = new AgentPipelineProperties();
        props.setSequence(List.of(
                "requirement", "impact", "test-design", "implementation", "review", "realisation", "evidence"));
        runStore = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
        // A mock always returns false from pauseAfter(...) (Mockito's boolean default), so an
        // ungated run's pipeline() behaviour stays exactly as before this batch (AC-04) — the gate
        // coordinator's own behaviour is covered by ApprovalGateCoordinatorTest.
        gateCoordinator = mock(ApprovalGateCoordinator.class);
        gateRegistry = mock(ApprovalGateRegistry.class);
        orchestrator = new EmbabelOrchestrator(requirementAgent, impactAnalysisAgent,
                testDesignAgent, implementationAgent, reviewAgent, evidenceAgent, props,
                asyncPipelineRunner, specGitPublisher, codeRealisationService, runStore, gateCoordinator,
                gateRegistry, mock(nl.metafactory.agents.workflowtrigger.WorkflowOrbRunner.class),
                mock(nl.metafactory.agents.workflowtrigger.WorkflowTriggerCoordinator.class));
    }

    @Test
    void availableAgentsReturnsSevenAgentsInSequenceOrder() {
        var agents = orchestrator.availableAgents();
        assertThat(agents).hasSize(7);
        assertThat(agents.get(0).id()).isEqualTo("requirement");
        assertThat(agents.get(0).sequenceOrder()).isEqualTo(0);
        assertThat(agents.get(0).outputType()).isEqualTo("RequirementAnalysis");
        assertThat(agents.get(1).id()).isEqualTo("impact");
        assertThat(agents.get(1).inputType()).isEqualTo("RequirementAnalysis");
        assertThat(agents.get(5).id()).isEqualTo("realisation");
        assertThat(agents.get(5).outputType()).isEqualTo("CodeChangeSet");
        assertThat(agents.get(6).id()).isEqualTo("evidence");
        assertThat(agents.get(6).sequenceOrder()).isEqualTo(6);
    }

    @Test
    void getReturnsNotFoundForUnknownRunId() {
        // Deliberately updated for V8/BR-36/BR-37 (workflow-approval-gate batch B4): "NOT_FOUND"
        // was a silent HTTP-200 sentinel that made the dashboard poll forever; this is the
        // delivery's one intentional non-additive behaviour change (architecture §13.3 item 1),
        // and the frontend (WorkflowExecution.tsx) already treats RUN_STATE_LOST as terminal.
        var run = orchestrator.get("unknown-id");
        assertThat(run.status()).isEqualTo("RUN_STATE_LOST");
    }

    @Test
    void startCreatesRunWithExpectedFields() {
        var request = new AgentRunRequest("cust1", "spec.md", List.of("requirement"), "user", "https://github.com/org/repo", null, null, null, null, null, null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var run = orchestrator.start(request);
        assertThat(run.runId()).isNotBlank();
        assertThat(run.customerId()).isEqualTo("cust1");
        assertThat(run.specFile()).isEqualTo("spec.md");
        assertThat(run.repositoryUrl()).isEqualTo("https://github.com/org/repo");
    }

    @Test
    void pipelineHappyPathCompletesRun() {
        when(requirementAgent.analyzeRequirements(any())).thenReturn(ra);
        when(impactAnalysisAgent.analyzeImpact(any(), any())).thenReturn(ir);
        when(testDesignAgent.designTests(any())).thenReturn(tp);
        when(implementationAgent.plan(any(), any())).thenReturn(ip);
        when(reviewAgent.review(any(), any())).thenReturn(rr);
        when(evidenceAgent.compileEvidence(any(), any(), any(), any(), any())).thenReturn(bundle);

        var request = new AgentRunRequest("cust1", "file.md", List.of("requirement", "impact", "test-design", "implementation", "review", "realisation", "evidence"), "user", "", null, null, null, null, null, null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var started = orchestrator.start(request);

        var result = orchestrator.get(started.runId());
        assertThat(result.status()).isEqualTo("COMPLETED");
        // 2 events per agent (RUNNING + result, incl. realisation), plus 1 git event for the spec
        assertThat(result.events()).hasSize(15);
        assertThat(result.generatedArtifacts()).containsExactly(
                "git-branch:spec/wf-1-run",
                "git-branch:feat/wf-1-run",
                "pull-request:https://github.com/org/repo/pull/9");
        // In a realisation run no separate implementation-plan PR is published
        verify(specGitPublisher, never()).publishImplementation(any(), any(), any(), any(), any());
    }

    @Test
    void pipelineRealisesImplementationWhenRealisationSelected() {
        when(impactAnalysisAgent.analyzeImpact(any(), any())).thenReturn(ir);
        when(testDesignAgent.designTests(any())).thenReturn(tp);
        when(implementationAgent.plan(any(), any())).thenReturn(ip);
        when(reviewAgent.review(any(), any())).thenReturn(rr);

        var request = new AgentRunRequest("cust1", "spec-1.md",
                List.of("impact", "test-design", "implementation", "review", "realisation"),
                "workflow:wf-spec-realise", "https://github.com/org/repo.git", "bot", "secret", null, null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var started = orchestrator.start(request);

        verify(codeRealisationService).realise(eq(started.runId()), eq(request), eq(ip), eq(tp), eq(rr), any());
        verify(specGitPublisher, never()).publishImplementation(any(), any(), any(), any(), any());

        var result = orchestrator.get(started.runId());
        var events = result.events().stream()
                .filter(e -> e.agentId().equals("realisation")).toList();
        assertThat(events).hasSize(2);
        assertThat(events.get(1).status()).isEqualTo("OK");
        assertThat(events.get(1).evidenceRef()).isEqualTo("git://feat/wf-1-run");
        assertThat(result.generatedArtifacts()).containsExactly(
                "git-branch:feat/wf-1-run",
                "pull-request:https://github.com/org/repo/pull/9");
    }

    @Test
    void pipelineRecordsFailedRealisationEventButCompletesRun() {
        when(impactAnalysisAgent.analyzeImpact(any(), any())).thenReturn(ir);
        when(testDesignAgent.designTests(any())).thenReturn(tp);
        when(implementationAgent.plan(any(), any())).thenReturn(ip);
        when(reviewAgent.review(any(), any())).thenReturn(rr);
        when(codeRealisationService.realise(any(), any(), any(), any(), any(), any()))
                .thenReturn(StageChangeReports.publishFailed("feat/wf-1-run", "Push failed"));

        var request = new AgentRunRequest("cust1", "spec-1.md",
                List.of("impact", "test-design", "implementation", "review", "realisation"),
                "workflow:wf-spec-realise", "https://github.com/org/repo.git", null, null, null, null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var started = orchestrator.start(request);

        var result = orchestrator.get(started.runId());
        assertThat(result.status()).isEqualTo("COMPLETED");
        var lastRealisationEvent = result.events().stream()
                .filter(e -> e.agentId().equals("realisation")).reduce((a, b) -> b).orElseThrow();
        assertThat(lastRealisationEvent.status()).isEqualTo("FAILED");
        assertThat(result.generatedArtifacts()).isEmpty();
    }

    @Test
    void pipelinePublishesImplementationPlanWithPullRequestAfterReviewStage() {
        when(impactAnalysisAgent.analyzeImpact(any(), any())).thenReturn(ir);
        when(testDesignAgent.designTests(any())).thenReturn(tp);
        when(implementationAgent.plan(any(), any())).thenReturn(ip);
        when(reviewAgent.review(any(), any())).thenReturn(rr);

        var request = new AgentRunRequest("cust1", "spec-1.md",
                List.of("impact", "test-design", "implementation", "review"),
                "workflow:wf-spec-implement", "https://github.com/org/repo.git", "bot", "secret", null, null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var started = orchestrator.start(request);

        verify(specGitPublisher).publishImplementation(started.runId(), request, ip, tp, rr);
        verify(specGitPublisher, never()).publish(any(), any(), any());
        verify(codeRealisationService, never()).realise(any(), any(), any(), any(), any(), any());

        var result = orchestrator.get(started.runId());
        var gitEvent = result.events().stream()
                .filter(e -> e.agentId().equals("git")).findFirst().orElseThrow();
        assertThat(gitEvent.status()).isEqualTo("OK");
        assertThat(gitEvent.evidenceRef()).isEqualTo("git://impl/wf-1-run");
        assertThat(result.generatedArtifacts()).containsExactly(
                "git-branch:impl/wf-1-run",
                "pull-request:https://github.com/org/repo/pull/7");
    }

    @Test
    void pipelineSkipsImplementationPublicationWhenImplementationNotSelected() {
        when(requirementAgent.analyzeRequirements(any())).thenReturn(ra);

        var request = new AgentRunRequest("cust1", "file.md", List.of("requirement"), "user", "", null, null, null, null, null, null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        orchestrator.start(request);

        verify(specGitPublisher, never()).publishImplementation(any(), any(), any(), any(), any());
    }

    @Test
    void pipelinePublishesSpecToGitAfterRequirementStage() {
        when(requirementAgent.analyzeRequirements(any())).thenReturn(ra);
        when(evidenceAgent.compileEvidence(any(), any(), any(), any(), any())).thenReturn(bundle);

        var request = new AgentRunRequest("cust1", "create a spec", List.of("requirement"),
                "workflow:wf-1", "https://github.com/org/repo.git", "bot", "secret", null, null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var started = orchestrator.start(request);

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(specGitPublisher).publish(org.mockito.ArgumentMatchers.eq(started.runId()),
                requestCaptor.capture(), org.mockito.ArgumentMatchers.eq(ra));
        assertThat(requestCaptor.getValue().requestedBy()).isEqualTo("workflow:wf-1");
        assertThat(requestCaptor.getValue().customerId()).isEqualTo("cust1");
        assertThat(requestCaptor.getValue().repositoryUrl()).isEqualTo("https://github.com/org/repo.git");
        assertThat(requestCaptor.getValue().gitUsername()).isEqualTo("bot");
        assertThat(requestCaptor.getValue().gitToken()).isEqualTo("secret");

        var result = orchestrator.get(started.runId());
        var gitEvent = result.events().stream()
                .filter(e -> e.agentId().equals("git")).findFirst().orElseThrow();
        assertThat(gitEvent.status()).isEqualTo("OK");
        assertThat(gitEvent.evidenceRef()).isEqualTo("git://spec/wf-1-run");
        assertThat(result.generatedArtifacts()).containsExactly("git-branch:spec/wf-1-run");
    }

    @Test
    void pipelineRecordsSkippedGitEventWithoutArtifactWhenPublicationIsSkipped() {
        when(requirementAgent.analyzeRequirements(any())).thenReturn(ra);
        when(specGitPublisher.publish(any(), any(), any()))
                .thenReturn(SpecPublication.skipped("No repository URL configured"));

        var request = new AgentRunRequest("cust1", "create a spec", List.of("requirement"), "user", "", null, null, null, null, null, null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var started = orchestrator.start(request);

        var result = orchestrator.get(started.runId());
        var gitEvent = result.events().stream()
                .filter(e -> e.agentId().equals("git")).findFirst().orElseThrow();
        assertThat(gitEvent.status()).isEqualTo("SKIPPED");
        assertThat(gitEvent.evidenceRef()).isEmpty();
        assertThat(result.generatedArtifacts()).isEmpty();
    }

    @Test
    void pipelineRecordsFailedGitEventButCompletesRunWhenPublicationFails() {
        when(requirementAgent.analyzeRequirements(any())).thenReturn(ra);
        when(specGitPublisher.publish(any(), any(), any()))
                .thenReturn(SpecPublication.failed("spec/wf-1-run", "Push failed: no write permissions"));

        var request = new AgentRunRequest("cust1", "create a spec", List.of("requirement"),
                "workflow:wf-1", "https://github.com/org/repo.git", "bot", "secret", null, null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var started = orchestrator.start(request);

        var result = orchestrator.get(started.runId());
        assertThat(result.status()).isEqualTo("COMPLETED");
        var gitEvent = result.events().stream()
                .filter(e -> e.agentId().equals("git")).findFirst().orElseThrow();
        assertThat(gitEvent.status()).isEqualTo("FAILED");
        assertThat(gitEvent.title()).contains("Push failed");
        assertThat(result.generatedArtifacts()).isEmpty();
    }

    @Test
    void pipelineExceptionPathFailsRun() {
        when(requirementAgent.analyzeRequirements(any())).thenThrow(new RuntimeException("ai failure"));

        var request = new AgentRunRequest("cust1", "file.md", List.of("requirement", "impact", "test-design", "implementation", "review", "realisation", "evidence"), "user", "", null, null, null, null, null, null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var started = orchestrator.start(request);

        var result = orchestrator.get(started.runId());
        assertThat(result.status()).isEqualTo("FAILED");
    }

    @Test
    void pipelineWithRejectedReviewSetsWaitingStatus() {
        var rejectedRr = new ReviewReport("id", false, List.of("needs work"));
        when(requirementAgent.analyzeRequirements(any())).thenReturn(ra);
        when(impactAnalysisAgent.analyzeImpact(any(), any())).thenReturn(ir);
        when(testDesignAgent.designTests(any())).thenReturn(tp);
        when(implementationAgent.plan(any(), any())).thenReturn(ip);
        when(reviewAgent.review(any(), any())).thenReturn(rejectedRr);
        when(evidenceAgent.compileEvidence(any(), any(), any(), any(), any())).thenReturn(bundle);

        var request = new AgentRunRequest("cust1", "file.md", List.of("requirement", "impact", "test-design", "implementation", "review", "realisation", "evidence"), "user", "", null, null, null, null, null, null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var started = orchestrator.start(request);

        var result = orchestrator.get(started.runId());
        var reviewEvent = result.events().stream()
                .filter(e -> e.agentId().equals("review")).reduce((first, second) -> second).orElseThrow();
        assertThat(reviewEvent.status()).isEqualTo("WAITING");
        assertThat(result.status()).isEqualTo("COMPLETED");
    }

    // AC-08 security regression: a non-empty agentIds selection must run exactly those stages and
    // no others — this is the proof that the old "empty means all" widening path is gone.

    @Test
    void pipelineWithSelectedAgentIdsOnlyRunsThoseAgents() {
        when(requirementAgent.analyzeRequirements(any())).thenReturn(ra);
        when(evidenceAgent.compileEvidence(any(), any(), any(), any(), any())).thenReturn(bundle);

        var request = new AgentRunRequest("cust1", "file.md", List.of("requirement", "evidence"), "user", "", null, null, null, null, null, null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var started = orchestrator.start(request);

        var result = orchestrator.get(started.runId());
        assertThat(result.status()).isEqualTo("COMPLETED");

        verify(requirementAgent).analyzeRequirements(any());
        verify(evidenceAgent).compileEvidence(any(), any(), any(), any(), any());
        verifyNoInteractions(impactAnalysisAgent, testDesignAgent, implementationAgent, reviewAgent);

        var eventsByAgent = result.events().stream()
                .collect(java.util.stream.Collectors.groupingBy(e -> e.agentId()));
        assertThat(eventsByAgent.get("requirement")).hasSize(2);
        assertThat(eventsByAgent.get("evidence")).hasSize(2);
        assertThat(eventsByAgent.get("impact")).extracting(e -> e.status()).containsExactly("SKIPPED");
        assertThat(eventsByAgent.get("test-design")).extracting(e -> e.status()).containsExactly("SKIPPED");
        assertThat(eventsByAgent.get("implementation")).extracting(e -> e.status()).containsExactly("SKIPPED");
        assertThat(eventsByAgent.get("review")).extracting(e -> e.status()).containsExactly("SKIPPED");
    }

    @Test
    void pipelineSkipsRequirementAndEvidenceWhenNotSelected() {
        when(impactAnalysisAgent.analyzeImpact(any(), any())).thenReturn(ir);
        when(testDesignAgent.designTests(any())).thenReturn(tp);
        when(implementationAgent.plan(any(), any())).thenReturn(ip);
        when(reviewAgent.review(any(), any())).thenReturn(rr);

        var request = new AgentRunRequest("cust1", "file.md",
                List.of("impact", "test-design", "implementation", "review"), "user", "", null, null, null, null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var started = orchestrator.start(request);

        var result = orchestrator.get(started.runId());
        assertThat(result.status()).isEqualTo("COMPLETED");

        verifyNoInteractions(requirementAgent, evidenceAgent);
        verify(specGitPublisher, never()).publish(any(), any(), any());

        var eventsByAgent = result.events().stream()
                .collect(java.util.stream.Collectors.groupingBy(e -> e.agentId()));
        assertThat(eventsByAgent.get("requirement")).extracting(e -> e.status()).containsExactly("SKIPPED");
        assertThat(eventsByAgent.get("evidence")).extracting(e -> e.status()).containsExactly("SKIPPED");
        assertThat(eventsByAgent.get("impact")).hasSize(2);
        assertThat(eventsByAgent.get("test-design")).hasSize(2);
        assertThat(eventsByAgent.get("implementation")).hasSize(2);
        assertThat(eventsByAgent.get("review")).hasSize(2);
    }

    @Test
    void pipelineIgnoresUnknownAgentIdsWithoutFailing() {
        when(requirementAgent.analyzeRequirements(any())).thenReturn(ra);
        when(evidenceAgent.compileEvidence(any(), any(), any(), any(), any())).thenReturn(bundle);

        var request = new AgentRunRequest("cust1", "file.md",
                List.of("requirement", "evidence", "does-not-exist"), "user", "", null, null, null, null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var started = orchestrator.start(request);

        var result = orchestrator.get(started.runId());
        assertThat(result.status()).isEqualTo("COMPLETED");
    }

    @Test
    void pipelineHaltsAsCancelledWhenStoppedBeforeItRuns() {
        Runnable[] captured = new Runnable[1];
        doAnswer(inv -> { captured[0] = inv.getArgument(0); return null; })
                .when(asyncPipelineRunner).run(any());

        var request = new AgentRunRequest("cust1", "file.md", List.of("requirement", "impact", "test-design", "implementation", "review", "realisation", "evidence"), "user", "", null, null, null, null, null, null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var started = orchestrator.start(request);

        orchestrator.stop(started.runId());
        captured[0].run();

        var result = orchestrator.get(started.runId());
        assertThat(result.status()).isEqualTo("CANCELLED");
        verifyNoInteractions(requirementAgent);
    }

    @Test
    void startThrowsWhenAgentIdsIsEmpty() {
        var request = new AgentRunRequest("cust1", "file.md", List.of(), "user", "", null, null, null, null, null, null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        assertThatThrownBy(() -> orchestrator.start(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AgentRunRequest.agentIds must not be null or empty");
    }

    @Test
    void startThrowsWhenAgentIdsIsNull() {
        var request = new AgentRunRequest("cust1", "file.md", null, "user", "", null, null, null, null, null, null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        assertThatThrownBy(() -> orchestrator.start(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AgentRunRequest.agentIds must not be null or empty");
    }

}
