package nl.metafactory.agents.approval;

import nl.metafactory.agents.approval.model.ApprovalDecisionCommand;
import nl.metafactory.agents.approval.model.ApprovalDecisionKind;
import nl.metafactory.agents.approval.model.ApprovalGateConfig;
import nl.metafactory.agents.approval.model.ApprovalGateState;
import nl.metafactory.agents.config.ApprovalGateProperties;
import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.orchestration.AgentRunStore;
import nl.metafactory.agents.persistence.InMemoryAgentRunPersistence;
import nl.metafactory.agents.orchestration.PipelineContinuation;
import nl.metafactory.agents.orchestration.PipelineState;
import nl.metafactory.agents.workflow.YamlDefinitionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ApprovalDecisionServiceTest {

    private AgentRunStore runStore;
    private ApprovalGateRegistry registry;
    private ApprovalDecisionAuditRepository auditRepository;
    private PipelineContinuation continuation;
    private ApprovalDecisionService service;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        runStore = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
        registry = new ApprovalGateRegistry();
        var properties = new WorkflowDefinitionProperties();
        properties.setPath(tempDir.toString());
        auditRepository = new ApprovalDecisionAuditRepository(properties, new YamlDefinitionStore());
        continuation = mock(PipelineContinuation.class);
        service = new ApprovalDecisionService(registry, runStore, auditRepository,
                new ApprovalGateProperties(), continuation);
    }

    private PipelineState parkedState(String runId, int iteration, String placementStage) {
        runStore.create(runId, "cust1", "spec.md", "https://github.com/org/repo", null, null);
        runStore.setStatus(runId, "AWAITING_APPROVAL");
        var request = new AgentRunRequest("cust1", "spec.md", List.of("realisation"), "workflow:wf-1",
                "https://github.com/org/repo", null, null, new ApprovalGateConfig(true, placementStage), null, null,
                null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var state = new PipelineState(runId, new SpecContent(runId, "spec.md", "spec.md", ""),
                Set.of("realisation"), request);
        var gate = new ApprovalGateState(placementStage, "realisation".equals(placementStage), 3);
        for (int i = 1; i < iteration; i++) {
            gate.incrementIteration();
            gate.markDecided(i);
        }
        gate.incrementIteration();
        gate.setOpen(true);
        gate.setLastReport(StageChangeReports.published("feat/wf-1-run", "https://github.com/org/repo/pull/9",
                "summary", List.of("a.txt"), "message"));
        state.setGate(gate);
        registry.park(runId, state);
        return state;
    }

    @Test
    void acceptResumesTheRunAndRecordsExactlyOneAuditEntry() {
        parkedState("run-1", 1, "realisation");

        var result = service.submit("run-1", new ApprovalDecisionCommand(ApprovalDecisionKind.ACCEPT, 1,
                null, "alice", "sub-1"));

        assertThat(result.status()).isEqualTo("RUNNING");
        assertThat(result.iteration()).isEqualTo(1);
        assertThat(runStore.get("run-1").status()).isEqualTo("RUNNING");
        verify(continuation).resume(any());
        assertThat(registry.isParked("run-1")).isFalse();

        var entries = auditRepository.findByRunId("run-1");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).decision()).isEqualTo(ApprovalDecisionKind.ACCEPT);
        assertThat(entries.get(0).actorUsername()).isEqualTo("alice");
        assertThat(entries.get(0).actorSubject()).isEqualTo("sub-1");
        assertThat(entries.get(0).workflowId()).isEqualTo("wf-1");
        assertThat(entries.get(0).gateStage()).isEqualTo("realisation");
    }

    @Test
    void denyReachesATerminalStatusDistinctFromFailedAndCancelledAndDoesNotResume() {
        parkedState("run-1", 1, "realisation");

        var result = service.submit("run-1", new ApprovalDecisionCommand(ApprovalDecisionKind.DENY, 1,
                null, "alice", "sub-1"));

        assertThat(result.status()).isEqualTo("DENIED");
        assertThat(runStore.get("run-1").status()).isEqualTo("DENIED");
        verify(continuation, never()).resume(any());
        assertThat(auditRepository.findByRunId("run-1")).extracting(e -> e.decision())
                .containsExactly(ApprovalDecisionKind.DENY);
    }

    @Test
    void denyWorksIdenticallyAtALaterIterationAfterEarlierDecisions() {
        // Stands in for AC-16/AC-17 (deny at iteration 3 after two feedback loops): the mechanics
        // are iteration-number-agnostic. The actual loop-back that produces iteration 3 lands in
        // batch B8; this proves DENY's handling already generalises to it.
        parkedState("run-1", 3, "realisation");

        var result = service.submit("run-1", new ApprovalDecisionCommand(ApprovalDecisionKind.DENY, 3,
                null, "bob", "sub-2"));

        assertThat(result.status()).isEqualTo("DENIED");
        assertThat(result.iteration()).isEqualTo(3);
        assertThat(auditRepository.findByRunId("run-1")).hasSize(1);
        assertThat(auditRepository.findByRunId("run-1").get(0).iteration()).isEqualTo(3);
    }

    @Test
    void aSecondDecisionAgainstTheSameIterationIsRejectedAsConflictAndAuditRetainsExactlyOne() {
        parkedState("run-1", 1, "realisation");
        service.submit("run-1", new ApprovalDecisionCommand(ApprovalDecisionKind.ACCEPT, 1, null, "alice", "sub-1"));

        assertThatThrownBy(() -> service.submit("run-1",
                new ApprovalDecisionCommand(ApprovalDecisionKind.DENY, 1, null, "bob", "sub-2")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(e -> assertThat(((org.springframework.web.server.ResponseStatusException) e)
                        .getStatusCode().value()).isEqualTo(409));

        assertThat(auditRepository.findByRunId("run-1")).hasSize(1);
        verify(continuation, times(1)).resume(any());
    }

    @Test
    void aDecisionAgainstARunThatIsNotAwaitingApprovalIsRejectedAsConflict() {
        runStore.create("run-1", "cust1", "spec.md", "", null, null);
        runStore.setStatus("run-1", "RUNNING");
        // Not parked at all.

        assertThatThrownBy(() -> service.submit("run-1",
                new ApprovalDecisionCommand(ApprovalDecisionKind.ACCEPT, 1, null, "alice", "sub-1")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(e -> assertThat(((org.springframework.web.server.ResponseStatusException) e)
                        .getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void aDecisionForAnUnknownRunIsRejectedAsNotFoundAndNothingIsCreated() {
        assertThatThrownBy(() -> service.submit("missing-run",
                new ApprovalDecisionCommand(ApprovalDecisionKind.ACCEPT, 1, null, "alice", "sub-1")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(e -> assertThat(((org.springframework.web.server.ResponseStatusException) e)
                        .getStatusCode().value()).isEqualTo(404));

        assertThat(auditRepository.findByRunId("missing-run")).isEmpty();
    }

    @Test
    void aDecisionAgainstTheWrongIterationNumberIsRejectedAsConflict() {
        parkedState("run-1", 1, "realisation");

        assertThatThrownBy(() -> service.submit("run-1",
                new ApprovalDecisionCommand(ApprovalDecisionKind.ACCEPT, 2, null, "alice", "sub-1")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(e -> assertThat(((org.springframework.web.server.ResponseStatusException) e)
                        .getStatusCode().value()).isEqualTo(409));

        assertThat(registry.isParked("run-1")).isTrue();
    }

    @Test
    void aDecisionAgainstAParkedRunWhoseGateIsNotOpenIsRejectedAsConflict() {
        // Defensive check: a genuinely parked run's gate is always open (pauseAfter sets it just
        // before parking), but validateOrReject must not assume that invariant blindly.
        var state = parkedState("run-1", 1, "realisation");
        state.gate().setOpen(false);

        assertThatThrownBy(() -> service.submit("run-1",
                new ApprovalDecisionCommand(ApprovalDecisionKind.ACCEPT, 1, null, "alice", "sub-1")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(e -> assertThat(((org.springframework.web.server.ResponseStatusException) e)
                        .getStatusCode().value()).isEqualTo(409));

        assertThat(registry.isParked("run-1")).isTrue();
    }

    // ── Batch B8: ACCEPT_WITH_COMMENTS / loop-back ──────────────────────────────

    @Test
    void acceptWithCommentsOnARealisationGateResumesWithPendingFeedbackRecorded() {
        // The realisation stage's completedStages bookkeeping (package-private to
        // nl.metafactory.agents.orchestration) is proven end-to-end, including the actual
        // re-execution of CodeRealisationService, by EmbabelOrchestratorPauseResumeTest — this
        // test asserts everything ApprovalDecisionService itself is responsible for.
        var state = parkedState("run-1", 1, "realisation");

        var result = service.submit("run-1", new ApprovalDecisionCommand(
                ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 1, "please rename computeTotal", "alice", "sub-1"));

        assertThat(result.status()).isEqualTo("RUNNING");
        assertThat(state.gate().pendingFeedback()).isEqualTo("please rename computeTotal");
        assertThat(state.gate().commentHistory()).hasSize(1);
        assertThat(state.gate().commentHistory().get(0).comment()).isEqualTo("please rename computeTotal");
        assertThat(state.gate().isOpen()).isFalse();
        verify(continuation).resume(state);
        assertThat(auditRepository.findByRunId("run-1")).hasSize(1);
        assertThat(auditRepository.findByRunId("run-1").get(0).comment()).isEqualTo("please rename computeTotal");
    }

    @Test
    void acceptWithCommentsIsRejectedForANonRealisationPlacementAndNothingIsTouched() {
        // AC-59: feedbackSupported is read from the gate (computed once in
        // ApprovalGateCoordinator), never re-derived from the placement stage name here.
        parkedState("run-1", 1, "impact");

        assertThatThrownBy(() -> service.submit("run-1", new ApprovalDecisionCommand(
                ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 1, "please fix x", "alice", "sub-1")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(e -> assertThat(((org.springframework.web.server.ResponseStatusException) e)
                        .getStatusCode().value()).isEqualTo(400))
                .hasMessageContaining("impact");

        assertThat(auditRepository.findByRunId("run-1")).isEmpty();
        verify(continuation, never()).resume(any());
        assertThat(registry.isParked("run-1")).isTrue();
    }

    @Test
    void acceptWithCommentsIsRejectedOnceTheFeedbackBoundIsExhausted() {
        // BR-27: max is 3, so iteration 4 (the 4th opening) is the final review — Accept/Deny only.
        parkedState("run-1", 4, "realisation");

        assertThatThrownBy(() -> service.submit("run-1", new ApprovalDecisionCommand(
                ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 4, "one more try", "alice", "sub-1")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(e -> assertThat(((org.springframework.web.server.ResponseStatusException) e)
                        .getStatusCode().value()).isEqualTo(400));

        assertThat(auditRepository.findByRunId("run-1")).isEmpty();
        verify(continuation, never()).resume(any());
        assertThat(registry.isParked("run-1")).isTrue();
    }

    @Test
    void acceptWithCommentsAtTheLastAllowedIterationIsStillAccepted() {
        // Iteration 3 <= max(3): the 3rd->4th loop is still allowed (at most 4 openings total).
        parkedState("run-1", 3, "realisation");

        var result = service.submit("run-1", new ApprovalDecisionCommand(
                ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 3, "final tweak please", "alice", "sub-1"));

        assertThat(result.status()).isEqualTo("RUNNING");
        verify(continuation).resume(any());
    }

    @Test
    void acceptWithCommentsIsRejectedWhenTheCommentIsBlank() {
        parkedState("run-1", 1, "realisation");

        assertThatThrownBy(() -> service.submit("run-1", new ApprovalDecisionCommand(
                ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 1, "   ", "alice", "sub-1")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(e -> assertThat(((org.springframework.web.server.ResponseStatusException) e)
                        .getStatusCode().value()).isEqualTo(400));

        assertThat(auditRepository.findByRunId("run-1")).isEmpty();
        assertThat(registry.isParked("run-1")).isTrue();
    }

    @Test
    void acceptWithCommentsIsRejectedWhenTheCommentExceedsTheConfiguredMaxLength() {
        runStore.create("run-1", "cust1", "spec.md", "https://github.com/org/repo", null, null);
        runStore.setStatus("run-1", "AWAITING_APPROVAL");
        var request = new AgentRunRequest("cust1", "spec.md", List.of("realisation"), "workflow:wf-1",
                "https://github.com/org/repo", null, null, new ApprovalGateConfig(true, "realisation"), null, null,
                null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var state = new PipelineState("run-1", new SpecContent("run-1", "spec.md", "spec.md", ""),
                Set.of("realisation"), request);
        var gate = new ApprovalGateState("realisation", true, 3);
        gate.incrementIteration();
        gate.setOpen(true);
        gate.setLastReport(StageChangeReports.notApplicable("x"));
        state.setGate(gate);
        registry.park("run-1", state);
        var properties = new ApprovalGateProperties();
        properties.setCommentMaxLength(10);
        var strictService = new ApprovalDecisionService(registry, runStore, auditRepository, properties, continuation);

        assertThatThrownBy(() -> strictService.submit("run-1", new ApprovalDecisionCommand(
                ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 1, "this comment is way too long", "alice", "sub-1")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(e -> assertThat(((org.springframework.web.server.ResponseStatusException) e)
                        .getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void acceptWithCommentsAgainstAnUnparkedRunIsRejectedAsConflictWithoutNullPointer() {
        runStore.create("run-1", "cust1", "spec.md", "", null, null);
        runStore.setStatus("run-1", "RUNNING");

        assertThatThrownBy(() -> service.submit("run-1", new ApprovalDecisionCommand(
                ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 1, "feedback", "alice", "sub-1")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(e -> assertThat(((org.springframework.web.server.ResponseStatusException) e)
                        .getStatusCode().value()).isEqualTo(409));
    }
}
