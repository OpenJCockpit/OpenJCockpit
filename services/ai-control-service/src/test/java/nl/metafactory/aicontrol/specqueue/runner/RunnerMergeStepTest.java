package nl.metafactory.aicontrol.specqueue.runner;

import nl.metafactory.aicontrol.config.SpecQueueProperties;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.SpecQueueFailureReason;
import nl.metafactory.aicontrol.model.SpecQueueItemStatus;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import nl.metafactory.aicontrol.service.GitHubApiException;
import nl.metafactory.aicontrol.service.GitHubPort;
import nl.metafactory.aicontrol.service.GitHubPullRequestRef;
import nl.metafactory.aicontrol.service.GitHubPullRequestState;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItemPullRequest;
import nl.metafactory.aicontrol.specqueue.runner.RunnerGitHubAccessStep.Access;
import nl.metafactory.aicontrol.specqueue.runner.SpecQueueRunnerTransitions.MergeClaim;
import nl.metafactory.aicontrol.specqueue.runner.SpecQueueRunnerTransitions.PrObservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Branch-by-branch checks of the merge step with every collaborator mocked. */
class RunnerMergeStepTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Access.Ready READY =
            new Access.Ready(new GitHubPullRequestRef("o", "r", 7), "https://api.github.test", "tok");

    private final SpecQueueRunnerTransitions transitions = mock(SpecQueueRunnerTransitions.class);
    private final RunnerGitHubAccessStep accessStep = mock(RunnerGitHubAccessStep.class);
    private final GitHubPort gitHub = mock(GitHubPort.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final UUID projectId = UUID.randomUUID();
    private RunnerMergeStep step;
    private SpecQueueItem item;
    private SpecQueueItemPullRequest pr;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        ObjectProvider<Clock> clock = mock(ObjectProvider.class);
        when(clock.getIfAvailable(any())).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));
        step = new RunnerMergeStep(transitions, accessStep, gitHub, projects, new SpecQueueProperties(), clock);
        when(projects.findById(projectId)).thenReturn(Optional.of(mock(Project.class)));
        item = new SpecQueueItem(projectId, "a.md", "wf", null, true, 1, "s", null, NOW);
        pr = new SpecQueueItemPullRequest(item.getId(), "run-1", "https://github.com/o/r/pull/7", "o", "r", 7, NOW);
        when(transitions.currentRunPullRequests(item)).thenReturn(List.of(pr));
        when(accessStep.resolve(any(), anyString())).thenReturn(READY);
    }

    private static GitHubPullRequestState state(boolean open, boolean merged, String mergeableState, String head) {
        return new GitHubPullRequestState(open, merged, false, Boolean.TRUE, mergeableState, head, "main", 0, 0, 1);
    }

    private void reads(GitHubPullRequestState s) throws Exception {
        when(gitHub.readPullRequest(any(), anyString(), anyString())).thenReturn(s);
    }

    private void verifyFailed(SpecQueueFailureReason reason, String mergeResult) {
        verify(transitions).failActiveItem(projectId, item.getId(), SpecQueueItemStatus.MERGING, reason, mergeResult);
    }

    // ---- human merge

    @Test
    void awaitingMergeWithoutAnyPullRequestFailsAsNotCreated() {
        when(transitions.currentRunPullRequests(item)).thenReturn(List.of());
        step.pollAwaitingMerge(item);
        verify(transitions).failActiveItem(projectId, item.getId(), SpecQueueItemStatus.AWAITING_MERGE,
                SpecQueueFailureReason.PR_NOT_CREATED, null);
    }

    @Test
    void awaitingMergeSkipsAlreadyMergedPullRequestsWithoutReadingThem() {
        pr.markMerged(NOW);
        step.pollAwaitingMerge(item);
        verify(accessStep, never()).resolve(any(), anyString());
        verify(transitions).applyPullRequestObservations(projectId, item.getId(), Map.of());
    }

    @Test
    void awaitingMergeFailsWhenTheRepositoryCannotBeAccessed() {
        when(accessStep.resolve(any(), anyString()))
                .thenReturn(new Access.Unusable(SpecQueueFailureReason.PR_HOST_UNSUPPORTED));
        step.pollAwaitingMerge(item);
        verify(transitions).failActiveItem(projectId, item.getId(), SpecQueueItemStatus.AWAITING_MERGE,
                SpecQueueFailureReason.PR_HOST_UNSUPPORTED, null);
    }

    @Test
    void awaitingMergeRecordsWhatWasReadAndThePollErrorOfTheRest() throws Exception {
        var failing = new SpecQueueItemPullRequest(item.getId(), "run-1", "https://github.com/o/r/pull/8", "o", "r", 8, NOW);
        when(transitions.currentRunPullRequests(item)).thenReturn(List.of(pr, failing));
        when(gitHub.readPullRequest(eq(READY.ref()), anyString(), anyString())).thenReturn(state(false, true, "clean", "s"));
        when(accessStep.resolve(any(), eq(failing.getUrl()))).thenReturn(
                new Access.Ready(new GitHubPullRequestRef("o", "r", 8), "https://api.github.test", "tok"));
        when(gitHub.readPullRequest(eq(new GitHubPullRequestRef("o", "r", 8)), anyString(), anyString()))
                .thenThrow(new GitHubApiException(500, false));

        step.pollAwaitingMerge(item);

        verify(transitions).applyPullRequestObservations(projectId, item.getId(), Map.of(pr.getId(), PrObservation.MERGED));
        verify(transitions).recordPollError(projectId, RunnerPollErrorCodes.GITHUB_UNAVAILABLE);
    }

    @Test
    void awaitingMergeOnlyRecordsThePollErrorWhenNothingCouldBeRead() throws Exception {
        when(gitHub.readPullRequest(any(), anyString(), anyString())).thenThrow(new GitHubApiException(429, true));
        step.pollAwaitingMerge(item);
        verify(transitions, never()).applyPullRequestObservations(any(), any(), any());
        verify(transitions).recordPollError(projectId, RunnerPollErrorCodes.GITHUB_RATE_LIMITED);
    }

    @Test
    void awaitingMergeClassifiesOpenAndClosedPullRequests() throws Exception {
        reads(state(true, false, "clean", "s"));
        step.pollAwaitingMerge(item);
        verify(transitions).applyPullRequestObservations(projectId, item.getId(), Map.of(pr.getId(), PrObservation.OPEN));
        reads(state(false, false, "clean", "s"));
        step.pollAwaitingMerge(item);
        verify(transitions).applyPullRequestObservations(projectId, item.getId(), Map.of(pr.getId(), PrObservation.CLOSED_UNMERGED));
    }

    // ---- auto merge: preconditions

    @Test
    void mergingNeedsExactlyOnePullRequest() {
        when(transitions.currentRunPullRequests(item)).thenReturn(List.of(pr, pr));
        step.driveMerging(item);
        verifyFailed(SpecQueueFailureReason.MERGE_BLOCKED, "BLOCKED");
    }

    @Test
    void mergingStopsWhenAutoMergeWasWithdrawn() {
        when(transitions.downgradeIfAutoMergeNotPermitted(projectId, item.getId())).thenReturn(true);
        step.driveMerging(item);
        verify(accessStep, never()).resolve(any(), anyString());
    }

    @Test
    void mergingFailsWhenTheRepositoryCannotBeAccessed() {
        when(accessStep.resolve(any(), anyString()))
                .thenReturn(new Access.Unusable(SpecQueueFailureReason.PR_HOST_UNSUPPORTED));
        step.driveMerging(item);
        verifyFailed(SpecQueueFailureReason.PR_HOST_UNSUPPORTED, null);
    }

    @Test
    void mergingOnlyRecordsAPollErrorWhenTheReadFails() throws Exception {
        when(gitHub.readPullRequest(any(), anyString(), anyString())).thenThrow(new GitHubApiException(0, false));
        step.driveMerging(item);
        verify(transitions).recordPollError(projectId, RunnerPollErrorCodes.GITHUB_UNAVAILABLE);
        verify(transitions, never()).claimMergeAttempt(any(), any(), any());
    }

    // ---- auto merge: decision on the read pull request

    @Test
    void mergingMapsEachMergeabilityDecision() throws Exception {
        reads(state(false, true, "clean", "s"));
        step.driveMerging(item);
        verify(transitions).completeMerge(projectId, item.getId(), "ALREADY_MERGED");

        reads(state(false, false, "clean", "s"));
        step.driveMerging(item);
        verifyFailed(SpecQueueFailureReason.PR_CLOSED_UNMERGED, null);

        reads(state(true, false, "dirty", "s"));
        step.driveMerging(item);
        verifyFailed(SpecQueueFailureReason.MERGE_CONFLICT, "CONFLICT");

        reads(state(true, false, "blocked", "s"));
        step.driveMerging(item);
        verifyFailed(SpecQueueFailureReason.MERGE_BLOCKED, "BLOCKED");
    }

    @Test
    void mergingWaitsWhileThePullRequestIsNotMergeableYet() throws Exception {
        reads(state(true, false, "unknown", "s"));
        step.driveMerging(item);
        verify(transitions).recordPollSuccess(projectId);
        verify(transitions, never()).failActiveItem(any(), any(), any(), any(), any());
    }

    @Test
    void mergingGivesUpAfterTheWaitTimeout() throws Exception {
        item.moveTo(1, NOW.minusSeconds(24 * 3600));
        reads(state(true, false, "unknown", "s"));
        step.driveMerging(item);
        verifyFailed(SpecQueueFailureReason.MERGE_WAIT_TIMEOUT, "WAIT_TIMEOUT");
    }

    @Test
    void aMergeablePullRequestWithoutHeadShaIsTreatedAsNotReady() throws Exception {
        reads(state(true, false, "clean", " "));
        step.driveMerging(item);
        verify(transitions).recordPollSuccess(projectId);
        verify(transitions, never()).claimMergeAttempt(any(), any(), any());
    }

    // ---- auto merge: the single merge call

    @Test
    void noMergeCallIsSentWhenTheAttemptCouldNotBeClaimed() throws Exception {
        reads(state(true, false, "clean", "sha1"));
        when(transitions.claimMergeAttempt(projectId, item.getId(), "sha1")).thenReturn(MergeClaim.STALE);
        step.driveMerging(item);
        verify(gitHub, never()).squashMergePullRequest(any(), any(), any(), any(), any());
    }

    @Test
    void aSuccessfulSquashMergeCompletesTheItem() throws Exception {
        reads(state(true, false, "clean", "sha1"));
        when(transitions.claimMergeAttempt(projectId, item.getId(), "sha1")).thenReturn(MergeClaim.CLAIMED);
        step.driveMerging(item);
        verify(gitHub).squashMergePullRequest(READY.ref(), "sha1", "spec-queue: a.md", READY.apiUrl(), READY.token());
        verify(transitions).completeMerge(projectId, item.getId(), "MERGED");
    }

    private void mergeRefusedWith(GitHubApiException refusal) throws Exception {
        reads(state(true, false, "clean", "sha1"));
        when(transitions.claimMergeAttempt(projectId, item.getId(), "sha1")).thenReturn(MergeClaim.CLAIMED);
        org.mockito.Mockito.doThrow(refusal).when(gitHub).squashMergePullRequest(any(), any(), any(), any(), any());
    }

    @Test
    void anAuthRefusalFailsWithoutAnyFurtherCall() throws Exception {
        mergeRefusedWith(new GitHubApiException(403, false));
        step.driveMerging(item);
        verifyFailed(SpecQueueFailureReason.MERGE_AUTH_FAILED, "AUTH_FAILED");
        verify(gitHub, org.mockito.Mockito.times(1)).readPullRequest(any(), anyString(), anyString());
    }

    @Test
    void anUnclearMergeFailureIsDecidedByOneReadThatFindsItMerged() throws Exception {
        mergeRefusedWith(new GitHubApiException(500, false));
        when(gitHub.readPullRequest(any(), anyString(), anyString()))
                .thenReturn(state(true, false, "clean", "sha1"), state(false, true, "clean", "sha1"));
        step.driveMerging(item);
        verify(transitions).completeMerge(projectId, item.getId(), "MERGED");
    }

    @Test
    void aRefusedMergeThatLeftThePullRequestOpenFailsAsBlocked() throws Exception {
        mergeRefusedWith(new GitHubApiException(409, false));
        when(gitHub.readPullRequest(any(), anyString(), anyString()))
                .thenReturn(state(true, false, "clean", "sha1"), state(true, false, "clean", "sha1"));
        step.driveMerging(item);
        verifyFailed(SpecQueueFailureReason.MERGE_BLOCKED, "BLOCKED");
    }

    @Test
    void aRateLimitedMergeFailureIsNeverTreatedAsARefusal() throws Exception {
        mergeRefusedWith(new GitHubApiException(403, true));
        when(gitHub.readPullRequest(any(), anyString(), anyString()))
                .thenReturn(state(true, false, "clean", "sha1"))
                .thenThrow(new GitHubApiException(403, true));
        step.driveMerging(item);
        verify(transitions).recordPollError(projectId, RunnerPollErrorCodes.GITHUB_RATE_LIMITED);
        verify(transitions, never()).failActiveItem(any(), any(), any(), any(), any());
    }

    // ---- a merge attempt that was already recorded

    @Test
    void aRecordedAttemptWithinItsLeaseIsLeftAlone() {
        item.recordMergeAttempt(NOW.minusSeconds(1), "sha1");
        step.driveMerging(item);
        verify(accessStep, never()).resolve(any(), anyString());
        verify(transitions, never()).downgradeIfAutoMergeNotPermitted(any(), any());
    }

    @Test
    void anExpiredAttemptWithoutUsableAccessFailsAsOutcomeUnknown() {
        item.recordMergeAttempt(NOW.minusSeconds(24 * 3600), "sha1");
        when(accessStep.resolve(any(), anyString()))
                .thenReturn(new Access.Unusable(SpecQueueFailureReason.PR_HOST_UNSUPPORTED));
        step.driveMerging(item);
        verifyFailed(SpecQueueFailureReason.MERGE_OUTCOME_UNKNOWN, "OUTCOME_UNKNOWN");
    }

    @Test
    void anExpiredAttemptIsDecidedByOneReadAndNeverMergedAgain() throws Exception {
        item.recordMergeAttempt(NOW.minusSeconds(24 * 3600), "sha1");
        reads(state(true, false, "clean", "sha1"));
        step.driveMerging(item);
        verifyFailed(SpecQueueFailureReason.MERGE_OUTCOME_UNKNOWN, "OUTCOME_UNKNOWN");
        verify(gitHub, never()).squashMergePullRequest(any(), any(), any(), any(), any());
    }
}
