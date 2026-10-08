package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.agent.EvidenceAgent;
import nl.metafactory.agents.agent.ImpactAnalysisAgent;
import nl.metafactory.agents.agent.ImplementationAgent;
import nl.metafactory.agents.agent.RequirementAgent;
import nl.metafactory.agents.agent.ReviewAgent;
import nl.metafactory.agents.agent.TestDesignAgent;
import nl.metafactory.agents.approval.ApprovalGateCoordinator;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.approval.StageChangeReports;
import nl.metafactory.agents.approval.model.StageChangeReport;
import nl.metafactory.agents.workflowtrigger.WorkflowOrbRunner;
import nl.metafactory.agents.workflowtrigger.WorkflowTriggerCoordinator;
import nl.metafactory.agents.config.AgentPipelineProperties;
import nl.metafactory.agents.domain.ImpactReport;
import nl.metafactory.agents.domain.ImplementationPlan;
import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.ReviewReport;
import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.domain.TestPlan;
import nl.metafactory.agents.model.AgentDefinition;
import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.spec.CodeRealisationService;
import nl.metafactory.agents.spec.RealisationIteration;
import nl.metafactory.agents.spec.SpecGitPublisher;
import nl.metafactory.agents.spec.SpecPublication;
import nl.metafactory.agents.workflow.DefinitionFileReadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.stream.IntStream;

@Service
public class EmbabelOrchestrator implements AgentOrchestrator, PipelineContinuation {

    private static final Logger log = LoggerFactory.getLogger(EmbabelOrchestrator.class);
    private static final String WORKFLOW_DEFINITION_AGENT_ID = "workflow-definition";

    private static final Map<String, AgentDefinition> CATALOGUE = Map.ofEntries(
        Map.entry("requirement", new AgentDefinition("requirement", "Requirement Agent",
            "Retrieve and validate requirements", "specification",
            List.of("spec.read", "repository.search"), List.of("requirements.yaml"),
            0, "SpecContent", "RequirementAnalysis")),
        Map.entry("impact", new AgentDefinition("impact", "Impact Analysis Agent",
            "Perform impact analysis", "analysis",
            List.of("code.search", "api.catalog.read"), List.of("impact-analysis.md"),
            0, "RequirementAnalysis", "ImpactReport")),
        Map.entry("test-design", new AgentDefinition("test-design", "Test Design Agent",
            "Design test scenarios", "quality",
            List.of("test.catalog.read"), List.of("acceptance-tests.feature"),
            0, "ImpactReport", "TestPlan")),
        Map.entry("implementation", new AgentDefinition("implementation", "Implementation Agent",
            "Generate & modify code", "engineering",
            List.of("git.branch", "git.commit", "pr.create"), List.of("pull-request"),
            0, "TestPlan", "ImplementationPlan")),
        Map.entry("review", new AgentDefinition("review", "Review Agent",
            "Code review & quality check", "review",
            List.of("ci.read", "quality.read"), List.of("review-report.md"),
            0, "ImplementationPlan", "ReviewReport")),
        Map.entry("realisation", new AgentDefinition("realisation", "Realisation Agent",
            "Apply implementation in the repository", "engineering",
            List.of("git.branch", "git.read", "git.write", "git.commit", "pr.create"),
            List.of("pull-request"),
            0, "ImplementationPlan", "CodeChangeSet")),
        Map.entry("evidence", new AgentDefinition("evidence", "Evidence Agent",
            "Collect & log evidence", "evidence",
            List.of("evidence.write"), List.of("release-evidence.md"),
            0, "ReviewReport", "EvidenceBundle"))
    );

    private final RequirementAgent requirementAgent;
    private final ImpactAnalysisAgent impactAnalysisAgent;
    private final TestDesignAgent testDesignAgent;
    private final ImplementationAgent implementationAgent;
    private final ReviewAgent reviewAgent;
    private final EvidenceAgent evidenceAgent;
    private final AgentPipelineProperties pipelineProperties;
    private final AsyncPipelineRunner asyncPipelineRunner;
    private final SpecGitPublisher specGitPublisher;
    private final CodeRealisationService codeRealisationService;
    private final AgentRunStore runStore;
    private final ApprovalGateCoordinator gateCoordinator;
    private final ApprovalGateRegistry gateRegistry;
    private final WorkflowOrbRunner workflowOrbRunner;
    private final WorkflowTriggerCoordinator workflowTriggerCoordinator;

    public EmbabelOrchestrator(RequirementAgent requirementAgent,
                                ImpactAnalysisAgent impactAnalysisAgent,
                                TestDesignAgent testDesignAgent,
                                ImplementationAgent implementationAgent,
                                ReviewAgent reviewAgent,
                                EvidenceAgent evidenceAgent,
                                AgentPipelineProperties pipelineProperties,
                                AsyncPipelineRunner asyncPipelineRunner,
                                SpecGitPublisher specGitPublisher,
                                CodeRealisationService codeRealisationService,
                                AgentRunStore runStore,
                                ApprovalGateCoordinator gateCoordinator,
                                ApprovalGateRegistry gateRegistry,
                                WorkflowOrbRunner workflowOrbRunner,
                                WorkflowTriggerCoordinator workflowTriggerCoordinator) {
        this.requirementAgent = requirementAgent;
        this.impactAnalysisAgent = impactAnalysisAgent;
        this.testDesignAgent = testDesignAgent;
        this.implementationAgent = implementationAgent;
        this.reviewAgent = reviewAgent;
        this.evidenceAgent = evidenceAgent;
        this.pipelineProperties = pipelineProperties;
        this.asyncPipelineRunner = asyncPipelineRunner;
        this.specGitPublisher = specGitPublisher;
        this.codeRealisationService = codeRealisationService;
        this.runStore = runStore;
        this.gateCoordinator = gateCoordinator;
        this.gateRegistry = gateRegistry;
        this.workflowOrbRunner = workflowOrbRunner;
        this.workflowTriggerCoordinator = workflowTriggerCoordinator;
    }

    @Override
    public List<AgentDefinition> availableAgents() {
        var seq = pipelineProperties.getSequence();
        return IntStream.range(0, seq.size())
                .filter(i -> CATALOGUE.containsKey(seq.get(i)))
                .mapToObj(i -> {
                    var base = CATALOGUE.get(seq.get(i));
                    return new AgentDefinition(base.id(), base.name(), base.description(), base.role(),
                            base.allowedTools(), base.requiredOutputs(), i, base.inputType(), base.outputType());
                })
                .toList();
    }

    @Override
    public AgentRun start(AgentRunRequest request) {
        var runId = UUID.randomUUID().toString();
        var repoUrl = request.repositoryUrl() != null ? request.repositoryUrl() : "";
        var run = runStore.create(runId, request.customerId(), request.specFile(), repoUrl, request.workflowId(), request.startedBy());
        var spec = new SpecContent(runId, "spec.md", request.specFile(), repoUrl);
        // Invariant: agentIds must never be null or empty here — WorkflowExecutionService
        // guarantees at least one selected agent before calling start().
        if (request.agentIds() == null || request.agentIds().isEmpty()) {
            throw new IllegalArgumentException("AgentRunRequest.agentIds must not be null or empty");
        }
        Set<String> selectedAgentIds = Set.copyOf(request.agentIds());
        var state = new PipelineState(runId, spec, selectedAgentIds, request);
        asyncPipelineRunner.run(() -> pipeline(state));
        return run;
    }

    @Override
    public AgentRun get(String runId) {
        var run = runStore.get(runId);
        if (run != null) {
            return run;
        }
        // V8/BR-36/BR-37 (workflow-approval-gate): an id unknown to this instance — never seen,
        // or a paused run whose in-memory state was lost to a restart — is now a defined,
        // terminal, non-misleading outcome, not the old "NOT_FOUND with HTTP 200" sentinel that
        // made WorkflowExecution.tsx poll forever (its TERMINAL_STATUSES set never contained
        // "NOT_FOUND"). This is the delivery's one deliberate, documented non-additive behaviour
        // change (architecture §13.3 item 1) — affects every unknown run id, not only paused ones.
        // AC-13 / R8: a synthetic run has no real failure. The 12th component is explicitly null
        // and must never be given a fabricated summary — this run represents "state unknown",
        // not "the run failed for reason X".
        return new AgentRun(runId, "unknown", "unknown", "", "RUN_STATE_LOST", Instant.now(), List.of(), List.of(), null, null, null, null);
    }

    @Override
    public void stop(String runId) {
        runStore.markCancelled(runId);
        // A parked run has no thread left to observe checkCancelled (ADR-001), so Stop on a
        // parked run must act synchronously here rather than merely setting a flag (AC-34).
        PipelineState parked = gateRegistry.evict(runId);
        if (parked != null) {
            runStore.setStatus(runId, "CANCELLED");
        }
        workflowTriggerCoordinator.onParentStopped(runId);
    }

    /** {@link PipelineContinuation} — resumes a parked run from wherever it left off (ADR-001). */
    @Override
    public void resume(PipelineState state) {
        runStore.setStatus(state.runId(), "RUNNING");
        asyncPipelineRunner.run(() -> pipeline(state));
    }

    /**
     * Re-entrant pipeline continuation (ADR-001): each of the 7 stages is wrapped in
     * {@code if (!s.isDone(stageId))} so a resumed run skips work already completed in an earlier
     * pass, and {@link #endStage} is the single seam wired to {@link ApprovalGateCoordinator}
     * (ADR-003). For an ungated run — or a stage that isn't this workflow's configured gate —
     * {@code pauseAfter} always returns false, so the pipeline runs straight through in one pass
     * exactly as before (AC-04).
     */
    void pipeline(PipelineState s) {
        String runId = s.runId();
        try {
            if (workflowOrbRunner.runOrbsAnchoredTo(s, null)) return;
            if (!s.isDone("requirement")) {
                checkCancelled(runId);
                StageChangeReport report;
                if (s.selected("requirement")) {
                    recordEvent(runId, "requirement", "Retrieve and validate requirements...", "RUNNING", "");
                    s.ra = requirementAgent.analyzeRequirements(s.spec());
                    recordEvent(runId, "requirement", "Spec loaded and requirements extracted", "OK", "evidence://requirements");
                    report = publishSpecToGit(runId, s.request(), s.ra);
                } else {
                    s.ra = new RequirementAnalysis(runId, List.of(), "Skipped — not selected in workflow");
                    recordEvent(runId, "requirement", "Agent skipped (not in workflow)", "SKIPPED", "");
                    report = StageChangeReports.notApplicable("Requirement phase not selected in workflow");
                }
                if (endStage(s, "requirement", report)) return;
                if (workflowOrbRunner.runOrbsAnchoredTo(s, "requirement")) return;
            }

            if (!s.isDone("impact")) {
                checkCancelled(runId);
                StageChangeReport report;
                if (s.selected("impact")) {
                    recordEvent(runId, "impact", "Perform impact analysis...", "RUNNING", "");
                    s.ir = impactAnalysisAgent.analyzeImpact(s.spec(), s.ra);
                    recordEvent(runId, "impact", "Impact analysis recorded", "OK", "evidence://impact");
                    report = StageChangeReports.notApplicable(
                            "Risk level " + s.ir.riskLevel() + "; affected areas: " + s.ir.affectedAreas());
                } else {
                    s.ir = new ImpactReport(runId, List.of(), "UNKNOWN");
                    recordEvent(runId, "impact", "Agent skipped (not in workflow)", "SKIPPED", "");
                    report = StageChangeReports.notApplicable("Impact phase not selected in workflow");
                }
                if (endStage(s, "impact", report)) return;
                if (workflowOrbRunner.runOrbsAnchoredTo(s, "impact")) return;
            }

            if (!s.isDone("test-design")) {
                checkCancelled(runId);
                StageChangeReport report;
                if (s.selected("test-design")) {
                    recordEvent(runId, "test-design", "Design test scenarios...", "RUNNING", "");
                    s.tp = testDesignAgent.designTests(s.ra);
                    recordEvent(runId, "test-design", "Test set generated", "OK", "evidence://tests");
                    report = StageChangeReports.notApplicable(
                            s.tp.testCases().size() + " test case(s), coverage target " + s.tp.coverageTarget());
                } else {
                    s.tp = new TestPlan(runId, List.of(), "n/a");
                    recordEvent(runId, "test-design", "Agent skipped (not in workflow)", "SKIPPED", "");
                    report = StageChangeReports.notApplicable("Test design phase not selected in workflow");
                }
                if (endStage(s, "test-design", report)) return;
                if (workflowOrbRunner.runOrbsAnchoredTo(s, "test-design")) return;
            }

            if (!s.isDone("implementation")) {
                checkCancelled(runId);
                StageChangeReport report;
                if (s.selected("implementation")) {
                    recordEvent(runId, "implementation", "Generate & modify code...", "RUNNING", "");
                    s.ip = implementationAgent.plan(s.spec(), s.ir);
                    recordEvent(runId, "implementation", "Implementation proposal saved", "OK", "evidence://implementation");
                    report = StageChangeReports.notApplicable(s.ip.architectureDecision());
                } else {
                    s.ip = new ImplementationPlan(runId, List.of(), "n/a");
                    recordEvent(runId, "implementation", "Agent skipped (not in workflow)", "SKIPPED", "");
                    report = StageChangeReports.notApplicable("Implementation phase not selected in workflow");
                }
                if (endStage(s, "implementation", report)) return;
                if (workflowOrbRunner.runOrbsAnchoredTo(s, "implementation")) return;
            }

            if (!s.isDone("review")) {
                checkCancelled(runId);
                if (s.selected("review")) {
                    recordEvent(runId, "review", "Performing code review...", "RUNNING", "");
                    s.rr = reviewAgent.review(s.ip, s.tp);
                    var reviewStatus = s.rr.approved() ? "OK" : "WAITING";
                    recordEvent(runId, "review", "Review completed", reviewStatus, "evidence://review");
                } else {
                    s.rr = new ReviewReport(runId, true, List.of());
                    recordEvent(runId, "review", "Agent skipped (not in workflow)", "SKIPPED", "");
                }
                // In a realisation run the plan ends up in the feature branch itself;
                // a separate implementation-plan PR would duplicate it. This is part of the review
                // stage's own git publication (BR-07), so it happens inside review's block.
                StageChangeReport report;
                if (s.selected("implementation") && !s.selected("realisation")) {
                    report = publishImplementationToGit(runId, s.request(), s.ip, s.tp, s.rr);
                } else {
                    report = StageChangeReports.notApplicable(
                            "Review " + (s.rr.approved() ? "approved" : "not approved")
                                    + "; findings: " + s.rr.findings());
                }
                if (endStage(s, "review", report)) return;
                if (workflowOrbRunner.runOrbsAnchoredTo(s, "review")) return;
            }

            if (!s.isDone("realisation")) {
                checkCancelled(runId);
                StageChangeReport report;
                if (s.selected("realisation")) {
                    recordEvent(runId, "realisation", "Apply implementation in the repository...", "RUNNING", "");
                    // toPublication() (batch B5) reconstructs exactly today's SpecPublication for
                    // every StageOutcome, so an ungated run's AgentEvent/generatedArtifacts stay
                    // byte-identical (AC-04) even though CodeRealisationService returns the richer
                    // StageChangeReport the gate coordinator reads directly.
                    report = codeRealisationService.realise(runId, s.request(), s.ip, s.tp, s.rr,
                            realisationIterationFor(s));
                    recordPublication(runId, "realisation", report.toPublication());
                } else {
                    recordEvent(runId, "realisation", "Agent skipped (not in workflow)", "SKIPPED", "");
                    report = StageChangeReports.notApplicable("Realisation phase not selected in workflow");
                }
                if (endStage(s, "realisation", report)) return;
                if (workflowOrbRunner.runOrbsAnchoredTo(s, "realisation")) return;
            }

            if (!s.isDone("evidence")) {
                checkCancelled(runId);
                StageChangeReport report;
                if (s.selected("evidence")) {
                    recordEvent(runId, "evidence", "Collect & log evidence...", "RUNNING", "");
                    var bundle = evidenceAgent.compileEvidence(s.ra, s.ir, s.tp, s.ip, s.rr);
                    recordEvent(runId, "evidence", "Evidence data recorded", "OK", "evidence://bundle");
                    report = StageChangeReports.notApplicable(bundle.entries().size() + " evidence entry(ies) recorded");
                } else {
                    recordEvent(runId, "evidence", "Agent skipped (not in workflow)", "SKIPPED", "");
                    report = StageChangeReports.notApplicable("Evidence phase not selected in workflow");
                }
                if (endStage(s, "evidence", report)) return;
                if (workflowOrbRunner.runOrbsAnchoredTo(s, "evidence")) return;
            }

            completeRun(runId, "COMPLETED");
        } catch (CancellationException e) {
            completeRun(runId, "CANCELLED");
        } catch (Exception e) {
            log.error("Workflow pipeline failed for run {}", runId, e);
            if (e instanceof DefinitionFileReadException definitionFailure) {
                recordEvent(runId, WORKFLOW_DEFINITION_AGENT_ID,
                        "Workflow definition file could not be read: " + definitionFailure.relativeName()
                                + " — " + definitionFailure.sanitisedReason(),
                        "FAILED", "");
            }
            completeRun(runId, "FAILED", RunFailureDiagnostics.summarise(e));
        }
    }

    /**
     * The single seam wired to {@link ApprovalGateCoordinator#pauseAfter} (ADR-003). Marking the
     * stage completed is idempotent and always happens on the first pass; whether the run actually
     * pauses is entirely the coordinator's decision, driven by data on {@code report} — never a
     * separate control-flow branch here.
     */
    private boolean endStage(PipelineState s, String stageId, StageChangeReport report) {
        boolean firstPass = s.markCompleted(stageId);
        return firstPass && gateCoordinator.pauseAfter(s, stageId, report);
    }

    /**
     * Batch B8: derives which realisation attempt this pass is. A loop-back re-run is
     * distinguished by the gate actually being placed on "realisation" AND carrying pending
     * feedback (set by {@code ApprovalDecisionService} on {@code ACCEPT_WITH_COMMENTS}) — any
     * other gate placement, or a run with no gate at all, always gets {@link RealisationIteration#first()}
     * (AC-04/AC-59b: no other stage's re-run mechanics are ever exercised). Clears the consumed
     * feedback so a later, unrelated resume never accidentally replays stale operator text.
     */
    private RealisationIteration realisationIterationFor(PipelineState s) {
        var gate = s.gate();
        if (gate == null || !"realisation".equals(gate.placementStage()) || gate.pendingFeedback() == null) {
            return RealisationIteration.first();
        }
        String feedback = gate.pendingFeedback();
        var lastReport = gate.lastReport();
        List<String> previousPaths = lastReport != null && lastReport.changedPaths() != null
                ? lastReport.changedPaths() : List.of();
        var iteration = new RealisationIteration(gate.iteration() + 1, feedback, previousPaths,
                gate.retainedBranch(), gate.retainedPullRequestUrl());
        gate.setPendingFeedback(null);
        return iteration;
    }

    /**
     * Deterministic git steps: the spec from the requirement phase and the
     * implementation plan from the implementation/review phase are committed
     * on their own branch via the SpecGitPublisher (git-mcp-server), pushed
     * and opened as a pull request. A failed publication does not fail the run,
     * but is visible as a git event with status FAILED.
     */
    private StageChangeReport publishSpecToGit(String runId, AgentRunRequest request, RequirementAnalysis ra) {
        SpecPublication publication = specGitPublisher.publish(runId, request, ra);
        recordPublication(runId, "git", publication);
        return StageChangeReports.fromPublication(publication, List.of());
    }

    private StageChangeReport publishImplementationToGit(String runId, AgentRunRequest request,
                                            ImplementationPlan ip, TestPlan tp, ReviewReport rr) {
        SpecPublication publication = specGitPublisher.publishImplementation(runId, request, ip, tp, rr);
        recordPublication(runId, "git", publication);
        return StageChangeReports.fromPublication(publication, List.of());
    }

    private void recordPublication(String runId, String agentId, SpecPublication publication) {
        String evidenceRef = publication.branch() != null ? "git://" + publication.branch() : "";
        recordEvent(runId, agentId, publication.message(), publication.status(), evidenceRef);
        if (publication.isPublished()) {
            addArtifact(runId, "git-branch:" + publication.branch());
            if (publication.pullRequestUrl() != null) {
                addArtifact(runId, "pull-request:" + publication.pullRequestUrl());
            }
        }
    }

    private void checkCancelled(String runId) {
        if (runStore.isCancelled(runId)) {
            throw new CancellationException("Run cancelled: " + runId);
        }
    }

    private void recordEvent(String runId, String agentId, String title, String status, String evidenceRef) {
        runStore.recordEvent(runId, agentId, title, status, evidenceRef);
    }

    private void addArtifact(String runId, String artifact) {
        runStore.addArtifact(runId, artifact);
    }

    private void completeRun(String runId, String finalStatus) {
        completeRun(runId, finalStatus, null);
    }

    private void completeRun(String runId, String finalStatus, String failureSummary) {
        runStore.setStatus(runId, finalStatus, failureSummary);
    }
}
