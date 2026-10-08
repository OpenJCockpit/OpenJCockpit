package nl.metafactory.agents.approval.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Mutable per-run gate state (architecture §4.3), created the first time
 * {@code ApprovalGateCoordinator.pauseAfter} fires for a run and held by
 * {@code nl.metafactory.agents.orchestration.PipelineState} until the run is denied or accepted.
 * Not persisted — a restart loses it (BR-36), same as {@code PipelineState} itself.
 */
public class ApprovalGateState {

    private final String placementStage;
    private final boolean feedbackSupported;
    private final int maxFeedbackIterations;

    private int iteration;
    private boolean open;
    private boolean acceptedFinal;
    private final Set<Integer> decidedIterations = new LinkedHashSet<>();
    private final List<GateComment> commentHistory = new ArrayList<>();
    private String pendingFeedback;
    private StageChangeReport lastReport;
    private String retainedBranch;
    private String retainedPullRequestUrl;
    private Instant openedAt;

    public ApprovalGateState(String placementStage, boolean feedbackSupported, int maxFeedbackIterations) {
        this.placementStage = placementStage;
        this.feedbackSupported = feedbackSupported;
        this.maxFeedbackIterations = maxFeedbackIterations;
    }

    public String placementStage() {
        return placementStage;
    }

    public boolean feedbackSupported() {
        return feedbackSupported;
    }

    public int maxFeedbackIterations() {
        return maxFeedbackIterations;
    }

    public int iteration() {
        return iteration;
    }

    public void incrementIteration() {
        iteration++;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public boolean isAcceptedFinal() {
        return acceptedFinal;
    }

    public void setAcceptedFinal(boolean acceptedFinal) {
        this.acceptedFinal = acceptedFinal;
    }

    public Set<Integer> decidedIterations() {
        return decidedIterations;
    }

    public void markDecided(int iterationNumber) {
        decidedIterations.add(iterationNumber);
    }

    public List<GateComment> commentHistory() {
        return commentHistory;
    }

    public void addComment(GateComment comment) {
        commentHistory.add(comment);
    }

    public String pendingFeedback() {
        return pendingFeedback;
    }

    public void setPendingFeedback(String pendingFeedback) {
        this.pendingFeedback = pendingFeedback;
    }

    public StageChangeReport lastReport() {
        return lastReport;
    }

    public void setLastReport(StageChangeReport lastReport) {
        this.lastReport = lastReport;
    }

    public String retainedBranch() {
        return retainedBranch;
    }

    /** Monotonic: only ever assigned when a report actually supplies a branch. */
    public void retainBranchIfPresent(String branch) {
        if (branch != null) {
            this.retainedBranch = branch;
        }
    }

    public String retainedPullRequestUrl() {
        return retainedPullRequestUrl;
    }

    /** Monotonic (BR-22/R3): assigned only when currently null and the report supplies one. */
    public void retainPullRequestUrlIfAbsent(String pullRequestUrl) {
        if (this.retainedPullRequestUrl == null && pullRequestUrl != null) {
            this.retainedPullRequestUrl = pullRequestUrl;
        }
    }

    public Instant openedAt() {
        return openedAt;
    }

    public void markOpenedNow() {
        this.openedAt = Instant.now();
    }
}
