package nl.metafactory.agents.approval.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalGateStateTest {

    @Test
    void startsClosedAtIterationZeroWithNoDecisionsOrComments() {
        var gate = new ApprovalGateState("realisation", true, 3);

        assertThat(gate.placementStage()).isEqualTo("realisation");
        assertThat(gate.feedbackSupported()).isTrue();
        assertThat(gate.maxFeedbackIterations()).isEqualTo(3);
        assertThat(gate.iteration()).isZero();
        assertThat(gate.isOpen()).isFalse();
        assertThat(gate.isAcceptedFinal()).isFalse();
        assertThat(gate.decidedIterations()).isEmpty();
        assertThat(gate.commentHistory()).isEmpty();
        assertThat(gate.pendingFeedback()).isNull();
        assertThat(gate.lastReport()).isNull();
        assertThat(gate.retainedBranch()).isNull();
        assertThat(gate.retainedPullRequestUrl()).isNull();
        assertThat(gate.openedAt()).isNull();
    }

    @Test
    void setOpenTogglesTheOpenFlag() {
        var gate = new ApprovalGateState("realisation", true, 3);

        gate.setOpen(true);
        assertThat(gate.isOpen()).isTrue();

        gate.setOpen(false);
        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void setAcceptedFinalPreventsFurtherGateOpenings() {
        var gate = new ApprovalGateState("realisation", true, 3);

        gate.setAcceptedFinal(true);

        assertThat(gate.isAcceptedFinal()).isTrue();
    }

    @Test
    void markDecidedRecordsTheIterationNumberAndIsIdempotent() {
        var gate = new ApprovalGateState("realisation", true, 3);

        gate.markDecided(1);
        gate.markDecided(1);
        gate.markDecided(2);

        assertThat(gate.decidedIterations()).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void addCommentAppendsToTheHistoryWithoutLosingEarlierComments() {
        var gate = new ApprovalGateState("realisation", true, 3);
        var first = new GateComment(1, "alice", "please rename x", Instant.now());
        var second = new GateComment(2, "bob", "looks good now", Instant.now());

        gate.addComment(first);
        gate.addComment(second);

        assertThat(gate.commentHistory()).containsExactly(first, second);
    }

    @Test
    void pendingFeedbackCanBeSetAndCleared() {
        var gate = new ApprovalGateState("realisation", true, 3);

        gate.setPendingFeedback("please rename the variable");
        assertThat(gate.pendingFeedback()).isEqualTo("please rename the variable");

        gate.setPendingFeedback(null);
        assertThat(gate.pendingFeedback()).isNull();
    }

    @Test
    void incrementIterationCountsUpFromOne() {
        var gate = new ApprovalGateState("realisation", true, 3);

        gate.incrementIteration();
        assertThat(gate.iteration()).isEqualTo(1);

        gate.incrementIteration();
        assertThat(gate.iteration()).isEqualTo(2);
    }

    @Test
    void retainBranchIfPresentIgnoresNull() {
        var gate = new ApprovalGateState("realisation", true, 3);

        gate.retainBranchIfPresent(null);
        assertThat(gate.retainedBranch()).isNull();

        gate.retainBranchIfPresent("feat/wf-1-run");
        assertThat(gate.retainedBranch()).isEqualTo("feat/wf-1-run");

        // A later report's branch overwrites — branch retention is not monotonic like the PR URL.
        gate.retainBranchIfPresent("feat/wf-1-run-2");
        assertThat(gate.retainedBranch()).isEqualTo("feat/wf-1-run-2");
    }

    @Test
    void retainPullRequestUrlIfAbsentIsMonotonic() {
        var gate = new ApprovalGateState("realisation", true, 3);

        gate.retainPullRequestUrlIfAbsent(null);
        assertThat(gate.retainedPullRequestUrl()).isNull();

        gate.retainPullRequestUrlIfAbsent("https://github.com/org/repo/pull/9");
        assertThat(gate.retainedPullRequestUrl()).isEqualTo("https://github.com/org/repo/pull/9");

        // BR-22/R3: a later null or different value must not overwrite the retained URL.
        gate.retainPullRequestUrlIfAbsent(null);
        gate.retainPullRequestUrlIfAbsent("https://github.com/org/repo/pull/99");
        assertThat(gate.retainedPullRequestUrl()).isEqualTo("https://github.com/org/repo/pull/9");
    }

    @Test
    void setLastReportStoresTheMostRecentReport() {
        var gate = new ApprovalGateState("realisation", true, 3);
        var report = new StageChangeReport(StageOutcome.PUBLISHED, "feat/wf-1-run",
                "https://github.com/org/repo/pull/9", "summary", java.util.List.of("a.txt"), 1, null, "ok");

        gate.setLastReport(report);

        assertThat(gate.lastReport()).isEqualTo(report);
    }

    @Test
    void markOpenedNowSetsANonNullTimestamp() {
        var gate = new ApprovalGateState("realisation", true, 3);

        gate.markOpenedNow();

        assertThat(gate.openedAt()).isNotNull();
        assertThat(gate.openedAt()).isBeforeOrEqualTo(Instant.now());
    }
}
