package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.approval.StageChangeReports;
import nl.metafactory.agents.approval.model.ApprovalGateConfig;
import nl.metafactory.agents.approval.model.ApprovalGateState;
import nl.metafactory.agents.approval.model.GateComment;
import nl.metafactory.agents.approval.model.StageChangeReport;
import nl.metafactory.agents.approval.model.StageOutcome;
import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.gitworkspace.GitWorkspaceBinding;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.workflowrun.WorkflowRunScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A parked run's {@link PipelineState} survives a JSON round trip with everything a resumed pass reads. */
class PipelineStateCodecTest {

    private static final String DEV = "a2a:litellm:developer";
    private static final String QA = "a2a:litellm:qa";

    private static PipelineState fullState() {
        var request = new AgentRunRequest("cust1", "the spec", List.of("local:analyst", DEV, QA), "workflow:wf-1",
                "https://example.invalid/repo.git", "bob", "s3cr3t-token", new ApprovalGateConfig(true, DEV), "main",
                "wf-1", "alice", RunInitiator.human("alice", "sub-1"), List.of("wf-1"));
        var state = new PipelineState("run-1", new SpecContent("run-1", "spec.md", "the spec", "https://example.invalid/repo.git"),
                Set.copyOf(request.agentIds()), request);
        state.markCompleted("local:analyst");
        state.markCompleted(DEV);
        state.markOrbCompleted("orb-1");
        state.agentOutputs.put("local:analyst", "analysis");
        state.agentOutputs.put(DEV, "implementation");
        state.setWorkflowScope(new WorkflowRunScope("wf-1", "run-1",
                new GitWorkspaceBinding("ws-1", "https://example.invalid/repo.git", "feature/x")));
        var gate = new ApprovalGateState(DEV, true, 3);
        gate.incrementIteration();
        gate.markDecided(1);
        gate.addComment(new GateComment(1, "alice", "add tests", Instant.parse("2026-10-27T10:15:30.123456789Z")));
        gate.incrementIteration();
        gate.setOpen(true);
        gate.setPendingFeedback("pending");
        gate.setLastReport(new StageChangeReport(StageOutcome.PUBLISHED, "feature/x", "https://example.invalid/pr/1",
                "summary", List.of("a.txt", "b.txt"), 2, null, "published"));
        gate.retainBranchIfPresent("feature/x");
        gate.retainPullRequestUrlIfAbsent("https://example.invalid/pr/1");
        gate.markOpenedNow();
        state.setGate(gate);
        return state;
    }

    @Test
    void roundTripRestoresEverythingAResumedRunReads() {
        var original = fullState();

        var restored = PipelineStateCodec.read(PipelineStateCodec.write(original));

        assertThat(restored.runId()).isEqualTo("run-1");
        assertThat(restored.spec()).isEqualTo(original.spec());
        assertThat(restored.selected(QA)).isTrue();
        assertThat(restored.selected("local:other")).isFalse();
        assertThat(restored.isDone("local:analyst")).isTrue();
        assertThat(restored.isDone(DEV)).isTrue();
        assertThat(restored.isDone(QA)).isFalse();
        assertThat(restored.orbDone("orb-1")).isTrue();
        assertThat(restored.agentOutputs).containsExactly(
                java.util.Map.entry("local:analyst", "analysis"), java.util.Map.entry(DEV, "implementation"));
        assertThat(restored.workflowScope()).isEqualTo(original.workflowScope());
        assertThat(restored.request().approvalGate()).isEqualTo(new ApprovalGateConfig(true, DEV));
        assertThat(restored.request().initiator()).isEqualTo(RunInitiator.human("alice", "sub-1"));
        assertThat(restored.request().agentIds()).containsExactly("local:analyst", DEV, QA);

        var gate = restored.gate();
        var source = original.gate();
        assertThat(gate.placementStage()).isEqualTo(DEV);
        assertThat(gate.feedbackSupported()).isTrue();
        assertThat(gate.maxFeedbackIterations()).isEqualTo(3);
        assertThat(gate.iteration()).isEqualTo(2);
        assertThat(gate.isOpen()).isTrue();
        assertThat(gate.isAcceptedFinal()).isFalse();
        assertThat(gate.decidedIterations()).containsExactly(1);
        assertThat(gate.commentHistory()).isEqualTo(source.commentHistory());
        assertThat(gate.pendingFeedback()).isEqualTo("pending");
        assertThat(gate.lastReport()).isEqualTo(source.lastReport());
        assertThat(gate.retainedBranch()).isEqualTo("feature/x");
        assertThat(gate.retainedPullRequestUrl()).isEqualTo("https://example.invalid/pr/1");
        assertThat(gate.openedAt()).isEqualTo(source.openedAt());
    }

    @Test
    void theGitTokenIsNeverPersisted() {
        String json = PipelineStateCodec.write(fullState());

        assertThat(json).doesNotContain("s3cr3t-token");
        var restored = PipelineStateCodec.read(json);
        assertThat(restored.request().gitToken()).isNull();
        assertThat(restored.request().gitUsername()).isEqualTo("bob");
    }

    @Test
    void aStateWithoutGateOrScopeRoundTrips() {
        var request = new AgentRunRequest("cust1", "spec", List.of(DEV), "user", "", null, null, null,
                null, null, null, RunInitiator.trigger("test"), List.of());
        var state = new PipelineState("run-2", new SpecContent("run-2", "spec.md", "spec", ""), Set.of(DEV), request);

        var restored = PipelineStateCodec.read(PipelineStateCodec.write(state));

        assertThat(restored.gate()).isNull();
        assertThat(restored.workflowScope()).isNull();
        assertThat(restored.isDone(DEV)).isFalse();
    }

    @Test
    void aGateWithoutAReportRoundTrips() {
        var state = fullState();
        var gate = new ApprovalGateState(DEV, false, 1);
        gate.setLastReport(StageChangeReports.notApplicable("x"));
        state.setGate(gate);

        var restored = PipelineStateCodec.read(PipelineStateCodec.write(state));

        assertThat(restored.gate().feedbackSupported()).isFalse();
        assertThat(restored.gate().lastReport()).isEqualTo(gate.lastReport());
        assertThat(restored.gate().openedAt()).isNull();
    }

    @Test
    void aSnapshotInAnUnsupportedFormatIsRefused() {
        String json = PipelineStateCodec.write(fullState()).replace("\"formatVersion\":1", "\"formatVersion\":99");

        assertThatThrownBy(() -> PipelineStateCodec.read(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("99");
    }
}
