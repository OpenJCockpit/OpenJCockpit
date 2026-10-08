package nl.metafactory.agents.approval;

import nl.metafactory.agents.approval.model.ApprovalGateConfig;
import nl.metafactory.agents.approval.model.ApprovalGateState;
import nl.metafactory.agents.approval.model.StageChangeReport;
import nl.metafactory.agents.config.ApprovalGateProperties;
import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.orchestration.AgentRunStore;
import nl.metafactory.agents.persistence.InMemoryAgentRunPersistence;
import nl.metafactory.agents.orchestration.PipelineState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * AC-61: all four BR-40 outcomes reach the SAME gate-open seam (ADR-003) — asserted here against
 * one spied method with four crafted reports, not four independent scenario tests that each
 * happen to pass (risk 3). Also: {@code feedbackSupported} computed once per placement; monotonic
 * PR-URL retention; iteration increment; no re-fire once accepted; the early-return path for a
 * disabled/mismatched gate.
 */
class ApprovalGateCoordinatorTest {

    private AgentRunStore runStore;
    private ApprovalGateCoordinator coordinator;

    @BeforeEach
    void setUp() {
        runStore = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
        var registry = new ApprovalGateRegistry();
        var properties = new ApprovalGateProperties();
        coordinator = spy(new ApprovalGateCoordinator(registry, runStore, properties));
    }

    private PipelineState stateWithGate(String runId, String placementStage) {
        runStore.create(runId, "cust1", "spec.md", "https://github.com/org/repo", null, null);
        var request = new AgentRunRequest("cust1", "spec.md", List.of("realisation"), "workflow:wf-1",
                "https://github.com/org/repo", null, null, new ApprovalGateConfig(true, placementStage), null, null,
                null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        return new PipelineState(runId, new SpecContent(runId, "spec.md", "spec.md", ""), Set.of("realisation"), request);
    }

    @Test
    void allFourOutcomesReachTheSameSeamAndNeverFailTheRun() {
        var published = new StageChangeReport(nl.metafactory.agents.approval.model.StageOutcome.PUBLISHED,
                "feat/wf-1-run", "https://github.com/org/repo/pull/9", "summary", List.of("a.txt"), 1, null, "ok");
        var publishFailed = StageChangeReports.publishFailed("feat/wf-1-run", "Push failed");
        var noChange = StageChangeReports.noChange("feat/wf-1-run", "Nothing new");
        var notApplicable = StageChangeReports.notApplicable("No publication possible");

        for (StageChangeReport report : List.of(published, publishFailed, noChange, notApplicable)) {
            var state = stateWithGate("run-" + report.outcome(), "realisation");
            boolean paused = coordinator.pauseAfter(state, "realisation", report);
            assertThat(paused).as("outcome %s must pause the run", report.outcome()).isTrue();
            assertThat(runStore.get(state.runId()).status()).isEqualTo("AWAITING_APPROVAL");
        }

        verify(coordinator, times(4)).pauseAfter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("realisation"), org.mockito.ArgumentMatchers.any());
        assertThat(runStore.get("run-PUBLISHED").status()).isNotEqualTo("FAILED");
        assertThat(runStore.get("run-PUBLISH_FAILED").status()).isNotEqualTo("FAILED");
        assertThat(runStore.get("run-NO_CHANGE").status()).isNotEqualTo("FAILED");
        assertThat(runStore.get("run-NOT_APPLICABLE").status()).isNotEqualTo("FAILED");
    }

    @Test
    void feedbackSupportedIsTrueOnlyForRealisationPlacement() {
        var state = stateWithGate("run-realisation", "realisation");
        coordinator.pauseAfter(state, "realisation", StageChangeReports.notApplicable("x"));
        assertThat(state.gate().feedbackSupported()).isTrue();

        var otherState = stateWithGate("run-impact", "impact");
        coordinator.pauseAfter(otherState, "impact", StageChangeReports.notApplicable("x"));
        assertThat(otherState.gate().feedbackSupported()).isFalse();
    }

    @Test
    void pullRequestUrlRetentionIsMonotonicAcrossIterations() {
        var state = stateWithGate("run-1", "realisation");
        coordinator.pauseAfter(state, "realisation",
                StageChangeReports.published("feat/wf-1-run", "https://github.com/org/repo/pull/9", "s", List.of(), "m"));
        assertThat(state.gate().retainedPullRequestUrl()).isEqualTo("https://github.com/org/repo/pull/9");

        // A repeat PR-creation failure at iteration 2 must not blank the already-retained URL.
        coordinator.pauseAfter(state, "realisation",
                StageChangeReports.published("feat/wf-1-run", null, "s2", List.of(), "m2"));
        assertThat(state.gate().retainedPullRequestUrl()).isEqualTo("https://github.com/org/repo/pull/9");
        assertThat(state.gate().retainedBranch()).isEqualTo("feat/wf-1-run");
    }

    @Test
    void iterationIncrementsOnEachOpening() {
        var state = stateWithGate("run-1", "realisation");

        coordinator.pauseAfter(state, "realisation", StageChangeReports.notApplicable("x"));
        assertThat(state.gate().iteration()).isEqualTo(1);

        coordinator.pauseAfter(state, "realisation", StageChangeReports.notApplicable("y"));
        assertThat(state.gate().iteration()).isEqualTo(2);
    }

    @Test
    void doesNotReFireOnceAcceptedFinal() {
        var state = stateWithGate("run-1", "realisation");
        coordinator.pauseAfter(state, "realisation", StageChangeReports.notApplicable("x"));
        state.gate().setAcceptedFinal(true);

        boolean paused = coordinator.pauseAfter(state, "realisation", StageChangeReports.notApplicable("y"));

        assertThat(paused).isFalse();
        assertThat(state.gate().iteration()).isEqualTo(1);
    }

    @Test
    void returnsFalseWhenGateIsDisabled() {
        runStore.create("run-1", "cust1", "spec.md", "", null, null);
        var request = new AgentRunRequest("cust1", "spec.md", List.of("realisation"), "workflow:wf-1", "",
                null, null, new ApprovalGateConfig(false, "realisation"), null, null,
                null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var state = new PipelineState("run-1", new SpecContent("run-1", "spec.md", "spec.md", ""),
                Set.of("realisation"), request);

        boolean paused = coordinator.pauseAfter(state, "realisation", StageChangeReports.notApplicable("x"));

        assertThat(paused).isFalse();
        assertThat(state.gate()).isNull();
    }

    @Test
    void returnsFalseWhenStageIsNotThisWorkflowsGatePlacement() {
        var state = stateWithGate("run-1", "realisation");

        boolean paused = coordinator.pauseAfter(state, "impact", StageChangeReports.notApplicable("x"));

        assertThat(paused).isFalse();
        assertThat(state.gate()).isNull();
    }

    @Test
    void returnsFalseWhenApprovalGateConfigIsAbsentEntirely() {
        runStore.create("run-1", "cust1", "spec.md", "", null, null);
        var request = new AgentRunRequest("cust1", "spec.md", List.of("realisation"), "workflow:wf-1", "",
                null, null, null, null, null,
                null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var state = new PipelineState("run-1", new SpecContent("run-1", "spec.md", "spec.md", ""),
                Set.of("realisation"), request);

        boolean paused = coordinator.pauseAfter(state, "realisation", StageChangeReports.notApplicable("x"));

        assertThat(paused).isFalse();
    }

    @Test
    void gateStateIsCreatedOnceAndReusedAcrossIterations() {
        var state = stateWithGate("run-1", "realisation");

        coordinator.pauseAfter(state, "realisation", StageChangeReports.notApplicable("x"));
        ApprovalGateState firstGate = state.gate();
        coordinator.pauseAfter(state, "realisation", StageChangeReports.notApplicable("y"));

        assertThat(state.gate()).isSameAs(firstGate);
    }
}
