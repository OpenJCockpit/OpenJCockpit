package nl.metafactory.agents.approval;

import nl.metafactory.agents.approval.model.ApprovalGateConfig;
import nl.metafactory.agents.approval.model.ApprovalGateState;
import nl.metafactory.agents.approval.model.StageOutcome;
import nl.metafactory.agents.config.ApprovalGateProperties;
import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.orchestration.PipelineState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-13: the changed-path list is truncated to a documented bound with {@code omittedFileCount},
 * and no raw file body is ever included in the payload — the assembler consumes only
 * {@code StageChangeReport.changedPaths()}, never {@code FileChange.content()}.
 */
class ApprovalGateContextAssemblerTest {

    private PipelineState stateWithReport(String placementStage, List<String> changedPaths) {
        return stateWithReport(placementStage, changedPaths, 3);
    }

    private PipelineState stateWithReport(String placementStage, List<String> changedPaths, int maxFeedbackIterations) {
        var request = new AgentRunRequest("cust1", "spec.md", List.of("realisation"), "workflow:wf-1",
                "https://github.com/org/repo", null, null, new ApprovalGateConfig(true, placementStage), null, null,
                null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var state = new PipelineState("run-1", new SpecContent("run-1", "spec.md", "spec.md", ""),
                Set.of("realisation"), request);
        var gate = new ApprovalGateState(placementStage, "realisation".equals(placementStage), maxFeedbackIterations);
        gate.setLastReport(StageChangeReports.published("feat/wf-1-run", "https://github.com/org/repo/pull/9",
                "summary", changedPaths, "message"));
        gate.retainBranchIfPresent("feat/wf-1-run");
        gate.retainPullRequestUrlIfAbsent("https://github.com/org/repo/pull/9");
        gate.incrementIteration();
        gate.markOpenedNow();
        state.setGate(gate);
        return state;
    }

    @Test
    void truncatesChangedPathsToTheConfiguredBoundAndReportsOmittedCount() {
        var properties = new ApprovalGateProperties();
        properties.setMaxChangedPathsInContext(2);
        var assembler = new ApprovalGateContextAssembler(properties);
        var paths = IntStream.range(0, 5).mapToObj(i -> "file-" + i + ".txt").toList();
        var state = stateWithReport("realisation", paths);

        var context = assembler.assemble(state);

        assertThat(context.changedPaths()).containsExactly("file-0.txt", "file-1.txt");
        assertThat(context.omittedFileCount()).isEqualTo(3);
        assertThat(context.changedFileCount()).isEqualTo(5);
    }

    @Test
    void doesNotTruncateWhenUnderTheBound() {
        var properties = new ApprovalGateProperties();
        var assembler = new ApprovalGateContextAssembler(properties);
        var state = stateWithReport("realisation", List.of("a.txt", "b.txt"));

        var context = assembler.assemble(state);

        assertThat(context.changedPaths()).containsExactly("a.txt", "b.txt");
        assertThat(context.omittedFileCount()).isZero();
    }

    @Test
    void neverIncludesAFileBodyOnlyPaths() {
        var properties = new ApprovalGateProperties();
        var assembler = new ApprovalGateContextAssembler(properties);
        var state = stateWithReport("realisation", List.of("src/App.java"));

        var context = assembler.assemble(state);

        // StageChangeReport carries no file-body field at all; the context's field set is
        // exhaustively paths/counts/summary — this assertion pins that shape.
        assertThat(context.changedPaths()).containsExactly("src/App.java");
        assertThat(context.changeSummary()).isEqualTo("summary");
        assertThat(context.toString()).doesNotContain("class ").doesNotContain("public ");
    }

    @Test
    void reportsFeedbackSupportedAndFurtherFeedbackAllowedForRealisation() {
        var properties = new ApprovalGateProperties();
        var assembler = new ApprovalGateContextAssembler(properties);
        var state = stateWithReport("realisation", List.of());

        var context = assembler.assemble(state);

        assertThat(context.feedbackSupported()).isTrue();
        assertThat(context.furtherFeedbackAllowed()).isTrue();
        assertThat(context.iteration()).isEqualTo(1);
        assertThat(context.maxFeedbackIterations()).isEqualTo(3);
        assertThat(context.stageOutcome()).isEqualTo(StageOutcome.PUBLISHED);
        assertThat(context.branchName()).isEqualTo("feat/wf-1-run");
        assertThat(context.pullRequestUrl()).isEqualTo("https://github.com/org/repo/pull/9");
        assertThat(context.branchCompareUrl()).isNull();
        assertThat(context.workflowId()).isEqualTo("wf-1");
        assertThat(context.commentHistory()).isEmpty();
        assertThat(context.openedAt()).isNotNull();
    }

    @Test
    void furtherFeedbackAllowedIsFalseOnceIterationExceedsTheMaximum() {
        var properties = new ApprovalGateProperties();
        var assembler = new ApprovalGateContextAssembler(properties);
        var state = stateWithReport("realisation", List.of(), 1);
        state.gate().incrementIteration();

        var context = assembler.assemble(state);

        assertThat(context.iteration()).isEqualTo(2);
        assertThat(context.maxFeedbackIterations()).isEqualTo(1);
        assertThat(context.furtherFeedbackAllowed()).isFalse();
    }

    @Test
    void feedbackNotSupportedForANonRealisationPlacement() {
        var properties = new ApprovalGateProperties();
        var assembler = new ApprovalGateContextAssembler(properties);
        var state = stateWithReport("impact", List.of());

        var context = assembler.assemble(state);

        assertThat(context.feedbackSupported()).isFalse();
        assertThat(context.furtherFeedbackAllowed()).isFalse();
    }

    @Test
    void branchCompareUrlIsPresentOnlyWhenNoPullRequestUrlIsRetained() {
        var properties = new ApprovalGateProperties();
        var assembler = new ApprovalGateContextAssembler(properties);
        var request = new AgentRunRequest("cust1", "spec.md", List.of("realisation"), "workflow:wf-1",
                "https://github.com/org/repo.git", null, null, new ApprovalGateConfig(true, "realisation"), null, null,
                null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var state = new PipelineState("run-1", new SpecContent("run-1", "spec.md", "spec.md", ""),
                Set.of("realisation"), request);
        var gate = new ApprovalGateState("realisation", true, 3);
        gate.setLastReport(StageChangeReports.publishFailed("feat/wf-1-run", "Failed to create PR"));
        gate.retainBranchIfPresent("feat/wf-1-run");
        gate.incrementIteration();
        gate.markOpenedNow();
        state.setGate(gate);

        var context = assembler.assemble(state);

        assertThat(context.pullRequestUrl()).isNull();
        assertThat(context.branchCompareUrl()).isEqualTo("https://github.com/org/repo/compare/feat/wf-1-run?expand=1");
    }

    @Test
    void workflowIdIsNullWhenRequestedByHasNoWorkflowPrefix() {
        var properties = new ApprovalGateProperties();
        var assembler = new ApprovalGateContextAssembler(properties);
        var request = new AgentRunRequest("cust1", "spec.md", List.of("realisation"), "some-other-caller",
                "https://github.com/org/repo", null, null, new ApprovalGateConfig(true, "realisation"), null, null,
                null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var state = new PipelineState("run-1", new SpecContent("run-1", "spec.md", "spec.md", ""),
                Set.of("realisation"), request);
        var gate = new ApprovalGateState("realisation", true, 3);
        gate.setLastReport(StageChangeReports.notApplicable("x"));
        gate.incrementIteration();
        gate.markOpenedNow();
        state.setGate(gate);

        var context = assembler.assemble(state);

        assertThat(context.workflowId()).isNull();
    }

    @Test
    void branchCompareUrlIsNullWhenTheRepositoryUrlIsNotHttp() {
        var properties = new ApprovalGateProperties();
        var assembler = new ApprovalGateContextAssembler(properties);
        var request = new AgentRunRequest("cust1", "spec.md", List.of("realisation"), "workflow:wf-1",
                "git@github.com:org/repo.git", null, null, new ApprovalGateConfig(true, "realisation"), null, null,
                null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var state = new PipelineState("run-1", new SpecContent("run-1", "spec.md", "spec.md", ""),
                Set.of("realisation"), request);
        var gate = new ApprovalGateState("realisation", true, 3);
        gate.setLastReport(StageChangeReports.publishFailed("feat/wf-1-run", "Failed to create PR"));
        gate.retainBranchIfPresent("feat/wf-1-run");
        gate.incrementIteration();
        gate.markOpenedNow();
        state.setGate(gate);

        var context = assembler.assemble(state);

        assertThat(context.branchCompareUrl()).isNull();
    }
}
