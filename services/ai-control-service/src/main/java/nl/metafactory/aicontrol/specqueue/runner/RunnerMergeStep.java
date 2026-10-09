package nl.metafactory.aicontrol.specqueue.runner;

import nl.metafactory.aicontrol.config.SpecQueueProperties;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.SpecQueueFailureReason;
import nl.metafactory.aicontrol.model.SpecQueueItemStatus;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import nl.metafactory.aicontrol.service.GitHubApiException;
import nl.metafactory.aicontrol.service.GitHubPort;
import nl.metafactory.aicontrol.service.GitHubPullRequestState;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItemPullRequest;
import nl.metafactory.aicontrol.specqueue.github.MergeDecision;
import nl.metafactory.aicontrol.specqueue.github.MergeabilityEvaluator;
import nl.metafactory.aicontrol.specqueue.runner.RunnerGitHubAccessStep.Access;
import nl.metafactory.aicontrol.specqueue.runner.SpecQueueRunnerTransitions.PrObservation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Waits for a human merge or squash-merges once. At most one merge call is ever sent per item: the attempt is
 * committed before the call and an unclear outcome is decided only by a read.
 */
@Component
public class RunnerMergeStep {

    private final SpecQueueRunnerTransitions transitions;
    private final RunnerGitHubAccessStep accessStep;
    private final GitHubPort gitHub;
    private final ProjectRepository projects;
    private final SpecQueueProperties.Runner config;
    private final Clock clock;

    public RunnerMergeStep(SpecQueueRunnerTransitions transitions, RunnerGitHubAccessStep accessStep,
                           GitHubPort gitHub, ProjectRepository projects, SpecQueueProperties properties,
                           ObjectProvider<Clock> clock) {
        this.transitions = transitions;
        this.accessStep = accessStep;
        this.gitHub = gitHub;
        this.projects = projects;
        this.config = properties.getRunner();
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    // ── human merge ──────────────────────────────────────────────────────────

    public void pollAwaitingMerge(SpecQueueItem item) {
        UUID projectId = item.getProjectId();
        var prs = transitions.currentRunPullRequests(item);
        if (prs.isEmpty()) {
            fail(item, SpecQueueItemStatus.AWAITING_MERGE, SpecQueueFailureReason.PR_NOT_CREATED);
            return;
        }
        Project project = project(projectId);
        Map<UUID, PrObservation> observations = new HashMap<>();
        for (SpecQueueItemPullRequest pr : prs) {
            if (pr.getMergedAt() != null) {
                continue;
            }
            Access access = accessStep.resolve(project, pr.getUrl());
            if (access instanceof Access.Unusable unusable) {
                fail(item, SpecQueueItemStatus.AWAITING_MERGE, unusable.reason());
                return;
            }
            Optional<GitHubPullRequestState> state = read(projectId, (Access.Ready) access);
            if (state.isEmpty()) {
                return;
            }
            var s = state.get();
            observations.put(pr.getId(), s.merged() ? PrObservation.MERGED
                    : s.open() ? PrObservation.OPEN : PrObservation.CLOSED_UNMERGED);
        }
        transitions.applyPullRequestObservations(projectId, item.getId(), observations);
    }

    // ── auto merge ───────────────────────────────────────────────────────────

    public void driveMerging(SpecQueueItem item) {
        UUID projectId = item.getProjectId();
        var prs = transitions.currentRunPullRequests(item);
        if (prs.size() != 1) {
            fail(item, SpecQueueItemStatus.MERGING, SpecQueueFailureReason.MERGE_BLOCKED);
            return;
        }
        Project project = project(projectId);
        String prUrl = prs.get(0).getUrl();

        if (item.getMergeAttemptStartedAt() != null) {
            decideClaimedAttempt(item, project, prUrl);
            return;
        }
        if (transitions.downgradeIfAutoMergeNotPermitted(projectId, item.getId())) {
            return;
        }
        Access access = accessStep.resolve(project, prUrl);
        if (access instanceof Access.Unusable unusable) {
            fail(item, SpecQueueItemStatus.MERGING, unusable.reason());
            return;
        }
        var ready = (Access.Ready) access;
        Optional<GitHubPullRequestState> read = read(projectId, ready);
        if (read.isEmpty()) {
            return;
        }
        GitHubPullRequestState state = read.get();
        switch (MergeabilityEvaluator.evaluate(state)) {
            case ALREADY_MERGED -> transitions.completeMerge(projectId, item.getId(), "ALREADY_MERGED");
            case CLOSED_UNMERGED -> fail(item, SpecQueueItemStatus.MERGING, SpecQueueFailureReason.PR_CLOSED_UNMERGED);
            case CONFLICT -> fail(item, SpecQueueItemStatus.MERGING, SpecQueueFailureReason.MERGE_CONFLICT);
            case BLOCKED -> fail(item, SpecQueueItemStatus.MERGING, SpecQueueFailureReason.MERGE_BLOCKED);
            case WAIT -> transitions.recordPollSuccess(projectId);
            case MERGE -> mergeOnce(item, ready, state.headSha());
        }
    }

    /** A recorded attempt (possibly by a dead instance) is decided by one read after the lease; never a second merge. */
    private void decideClaimedAttempt(SpecQueueItem item, Project project, String prUrl) {
        if (!SpecQueueRunnerTransitions.isLeaseElapsed(item.getMergeAttemptStartedAt(), config.getMergeLease(), clock.instant())) {
            return;
        }
        Access access = accessStep.resolve(project, prUrl);
        if (access instanceof Access.Unusable) {
            fail(item, SpecQueueItemStatus.MERGING, SpecQueueFailureReason.MERGE_OUTCOME_UNKNOWN);
            return;
        }
        decideByRead(item, (Access.Ready) access, "MERGED");
    }

    private void mergeOnce(SpecQueueItem item, Access.Ready ready, String headSha) {
        UUID projectId = item.getProjectId();
        if (headSha == null || headSha.isBlank()) {
            transitions.recordPollSuccess(projectId);
            return;
        }
        var claim = transitions.claimMergeAttempt(projectId, item.getId(), headSha);
        if (claim != SpecQueueRunnerTransitions.MergeClaim.CLAIMED) {
            return;
        }
        try {
            gitHub.squashMergePullRequest(ready.ref(), headSha, "spec-queue: " + item.getSpecFile(),
                    ready.apiUrl(), ready.token());
            transitions.completeMerge(projectId, item.getId(), "MERGED");
        } catch (GitHubApiException e) {
            decideAfterFailedMerge(item, ready, e);
        }
    }

    private void decideAfterFailedMerge(SpecQueueItem item, Access.Ready ready, GitHubApiException e) {
        int status = e.status();
        if (!e.rateLimited() && (status == 405 || status == 409 || status == 422)) {
            decideByRead(item, ready, "ALREADY_MERGED", SpecQueueFailureReason.MERGE_BLOCKED);
        } else if (!e.rateLimited() && (status == 401 || status == 403 || status == 404)) {
            fail(item, SpecQueueItemStatus.MERGING, SpecQueueFailureReason.MERGE_AUTH_FAILED);
        } else {
            decideByRead(item, ready, "MERGED", SpecQueueFailureReason.MERGE_OUTCOME_UNKNOWN);
        }
    }

    private void decideByRead(SpecQueueItem item, Access.Ready ready, String mergedResult) {
        decideByRead(item, ready, mergedResult, SpecQueueFailureReason.MERGE_OUTCOME_UNKNOWN);
    }

    /** Exactly one read: merged completes, unmerged fails with the given reason, a failing read only records a poll error. */
    private void decideByRead(SpecQueueItem item, Access.Ready ready, String mergedResult,
                              SpecQueueFailureReason unmergedReason) {
        Optional<GitHubPullRequestState> state = read(item.getProjectId(), ready);
        if (state.isEmpty()) {
            return;
        }
        if (state.get().merged()) {
            transitions.completeMerge(item.getProjectId(), item.getId(), mergedResult);
        } else {
            fail(item, SpecQueueItemStatus.MERGING, unmergedReason);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Optional<GitHubPullRequestState> read(UUID projectId, Access.Ready ready) {
        try {
            return Optional.of(gitHub.readPullRequest(ready.ref(), ready.apiUrl(), ready.token()));
        } catch (GitHubApiException e) {
            transitions.recordPollError(projectId, RunnerPollErrorCodes.forGitHub(e));
            return Optional.empty();
        }
    }

    private void fail(SpecQueueItem item, SpecQueueItemStatus expected, SpecQueueFailureReason reason) {
        transitions.failActiveItem(item.getProjectId(), item.getId(), expected, reason, mergeResultFor(reason));
    }

    private static String mergeResultFor(SpecQueueFailureReason reason) {
        return switch (reason) {
            case MERGE_AUTH_FAILED -> "AUTH_FAILED";
            case MERGE_BLOCKED -> "BLOCKED";
            case MERGE_CONFLICT -> "CONFLICT";
            case MERGE_OUTCOME_UNKNOWN -> "OUTCOME_UNKNOWN";
            default -> null;
        };
    }

    private Project project(UUID projectId) {
        return projects.findById(projectId)
                .orElseThrow(() -> new IllegalStateException("Project of an active item is missing"));
    }
}
