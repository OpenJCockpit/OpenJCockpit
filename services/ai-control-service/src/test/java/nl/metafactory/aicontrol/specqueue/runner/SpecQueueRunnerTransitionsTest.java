package nl.metafactory.aicontrol.specqueue.runner;

import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.SpecQueueItemStatus;
import nl.metafactory.aicontrol.model.SpecQueueState;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueTransitions;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueEventType;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItemPullRequest;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueEventRepository;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueItemPullRequestRepository;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueItemRepository;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueRepository;
import nl.metafactory.aicontrol.specqueue.planner.FifoQueuePlanner;
import nl.metafactory.aicontrol.specqueue.planner.QueuePlanner;
import nl.metafactory.aicontrol.specqueue.planner.RunFinishedDecision;
import nl.metafactory.aicontrol.specqueue.planner.RunOutcome;
import nl.metafactory.aicontrol.specqueue.planner.RunPullRequest;
import nl.metafactory.aicontrol.specqueue.runner.SpecQueueRunnerTransitions.MergeClaim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/** Calls the runner's transaction bean directly, so every refusal and no-op branch is reached on a real database. */
@SpringBootTest
class SpecQueueRunnerTransitionsTest {

    private static final String PR_URL = "https://github.com/acme/repo/pull/5";

    @MockitoBean QueuePlanner planner;

    @Autowired SpecQueueRunnerTransitions runner;
    @Autowired SpecQueueTransitions transitions;
    @Autowired ProjectRepository projects;
    @Autowired SpecQueueRepository queues;
    @Autowired SpecQueueItemRepository items;
    @Autowired SpecQueueItemPullRequestRepository pullRequests;
    @Autowired SpecQueueEventRepository events;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;

    private final FifoQueuePlanner fifo = new FifoQueuePlanner();
    private UUID projectId;
    private long position;

    @BeforeEach
    void setUp() {
        for (String table : List.of("spec_queue_events", "spec_queue_item_pull_requests", "spec_queue_items",
                "spec_queues", "project_git_credentials")) {
            jdbc.update("delete from " + table);
        }
        jdbc.update("delete from projects");
        Project p = new Project();
        p.setName("proj");
        p.setGitUrl("https://github.com/acme/repo.git");
        projectId = projects.save(p).getId();
        transitions.ensureQueueRow(projectId);
        position = 0;
        when(planner.version()).thenReturn("TEST-v1");
        when(planner.selectNext(any(), any(), any())).thenAnswer(i -> fifo.selectNext(i.getArgument(0), i.getArgument(1), i.getArgument(2)));
        when(planner.onRunFinished(any(), any())).thenAnswer(i -> fifo.onRunFinished(i.getArgument(0), i.getArgument(1)));
    }

    private SpecQueueItem item(SpecQueueItemStatus status, boolean autoMerge) {
        var item = new SpecQueueItem(projectId, "spec-" + position + ".md", "wf", null, autoMerge, ++position, "s", null, Instant.now());
        if (status != SpecQueueItemStatus.QUEUED) {
            item.transitionTo(status, Instant.now());
        }
        if (status == SpecQueueItemStatus.RUNNING || status == SpecQueueItemStatus.MERGING
                || status == SpecQueueItemStatus.AWAITING_MERGE) {
            item.recordRun("run-1", Instant.now());
        }
        return items.saveAndFlush(item);
    }

    private SpecQueueItem reload(SpecQueueItem item) {
        return items.findById(item.getId()).orElseThrow();
    }

    private void allowAutoMerge() {
        tx.executeWithoutResult(s -> {
            var q = queues.findById(projectId).orElseThrow();
            q.changeAutoMergeAllowed(true, "test", Instant.now());
            queues.save(q);
        });
    }

    private long eventCount(SpecQueueEventType type) {
        return jdbc.queryForObject("select count(*) from spec_queue_events where event_type = ?", Long.class, type.name());
    }

    // ---- selection

    @Test
    void anEmptySelectionIsRecordedOnceAndForgottenWhenAnItemIsClaimed() {
        item(SpecQueueItemStatus.QUEUED, false);
        doReturn(Optional.empty()).when(planner).selectNext(any(), any(), any());

        assertThat(runner.claimNextItem(projectId)).isEmpty();
        assertThat(runner.claimNextItem(projectId)).isEmpty();
        assertThat(eventCount(SpecQueueEventType.SELECTED)).isEqualTo(1);
        assertThat(queues.findById(projectId).orElseThrow().getLastPlannerDecision()).isEqualTo("NONE");

        doAnswer(i -> fifo.selectNext(i.getArgument(0), i.getArgument(1), i.getArgument(2)))
                .when(planner).selectNext(any(), any(), any());
        assertThat(runner.claimNextItem(projectId)).isPresent();
        assertThat(queues.findById(projectId).orElseThrow().getLastPlannerDecision()).isNull();
    }

    @Test
    void anInvalidSelectionIsRecordedWithItsOverrideCode() {
        item(SpecQueueItemStatus.QUEUED, false);
        doReturn(Optional.of(UUID.randomUUID())).when(planner).selectNext(any(), any(), any());
        assertThat(runner.claimNextItem(projectId)).isEmpty();
        assertThat(queues.findById(projectId).orElseThrow().getLastPlannerDecision()).isEqualTo("INVALID_SELECTION");
    }

    // ---- start and observe

    @Test
    void recordStartedOnlyBindsAnItemThatIsStillStarting() {
        var queued = item(SpecQueueItemStatus.QUEUED, false);
        assertThat(runner.recordStarted(projectId, queued.getId(), "run-1", "RUNNING", Instant.now())).isFalse();
        assertThat(reload(queued).getStatus()).isEqualTo(SpecQueueItemStatus.QUEUED);
    }

    @Test
    void anOrphanRunIsAppendedAsAnEventWithoutTouchingTheItem() {
        var queued = item(SpecQueueItemStatus.QUEUED, false);
        runner.recordOrphanRun(projectId, queued.getId(), "wf", "run-9");
        assertThat(eventCount(SpecQueueEventType.ORPHAN_RUN_DETECTED)).isEqualTo(1);
        Optional<String> orphan = tx.execute(st -> transitions.findOrphanRunId(queued.getId()));
        assertThat(orphan).contains("run-9");
    }

    @Test
    void observationsUpdateTheStatusOnlyWhenItChangedAndIgnoreItemsThatAreNotRunning() {
        var running = item(SpecQueueItemStatus.RUNNING, false);
        assertThat(runner.recordObservation(projectId, running.getId(), "RUNNING")).isTrue();
        assertThat(reload(running).getLastRunStatus()).isEqualTo("RUNNING");
        assertThat(runner.recordObservation(projectId, running.getId(), "RUNNING")).isTrue();
        assertThat(runner.recordObservation(projectId, running.getId(), "AWAITING_APPROVAL")).isTrue();
        assertThat(reload(running).getLastRunStatus()).isEqualTo("AWAITING_APPROVAL");

        var queued = item(SpecQueueItemStatus.QUEUED, false);
        assertThat(runner.recordObservation(projectId, queued.getId(), "RUNNING")).isFalse();
    }

    @Test
    void cancellingByQueueIsANoOpForAnItemThatIsNotRunning() {
        var queued = item(SpecQueueItemStatus.QUEUED, false);
        assertThat(runner.cancelRunByQueue(projectId, queued.getId())).isFalse();
        assertThat(reload(queued).getStatus()).isEqualTo(SpecQueueItemStatus.QUEUED);
    }

    // ---- finishing a run

    @Test
    void unfinishedOutcomesCannotFinishARun() {
        var running = item(SpecQueueItemStatus.RUNNING, false);
        assertThatThrownBy(() -> runner.finishRun(projectId, running.getId(), new RunOutcome.NonTerminal("RUNNING"), null, "RUNNING"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runner.finishRun(projectId, running.getId(), new RunOutcome.CancelledByQueue(), null, "CANCELLED"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void finishingARunThatIsNoLongerRunningChangesNothing() {
        var queued = item(SpecQueueItemStatus.QUEUED, false);
        assertThat(runner.finishRun(projectId, queued.getId(), new RunOutcome.SucceededNoChanges(), null, "COMPLETED")).isFalse();
        assertThat(reload(queued).getStatus()).isEqualTo(SpecQueueItemStatus.QUEUED);
    }

    @Test
    void aPullRequestRecordedBeforeIsNotDuplicated() {
        var running = item(SpecQueueItemStatus.RUNNING, false);
        pullRequests.saveAndFlush(new SpecQueueItemPullRequest(running.getId(), "run-1", PR_URL, "acme", "repo", 5, Instant.now()));

        assertThat(runner.finishRun(projectId, running.getId(),
                new RunOutcome.SucceededWithPr(new RunPullRequest(PR_URL.toUpperCase().replace("HTTPS", "https"), "acme", "repo", 5)),
                null, "COMPLETED")).isTrue();

        assertThat(pullRequests.findByItemIdOrderByCreatedAtAsc(running.getId())).hasSize(1);
        assertThat(reload(running).getStatus()).isEqualTo(SpecQueueItemStatus.AWAITING_MERGE);
    }

    @Test
    void aPlannerThatHaltsAfterASuccessfulRunHaltsTheQueue() {
        var running = item(SpecQueueItemStatus.RUNNING, false);
        doReturn(RunFinishedDecision.HALT).when(planner).onRunFinished(any(), any());

        assertThat(runner.finishRun(projectId, running.getId(), new RunOutcome.SucceededNoChanges(), null, "COMPLETED")).isTrue();

        assertThat(reload(running).getStatus()).isEqualTo(SpecQueueItemStatus.COMPLETED_NO_CHANGES);
        assertThat(queues.findById(projectId).orElseThrow().getState()).isEqualTo(SpecQueueState.HALTED);
    }

    // ---- waiting for and performing the merge

    @Test
    void observingPullRequestsOfAnItemThatIsNotAwaitingMergeChangesNothing() {
        var running = item(SpecQueueItemStatus.RUNNING, false);
        assertThat(runner.applyPullRequestObservations(projectId, running.getId(), Map.of())).isFalse();
    }

    @Test
    void autoMergeThatIsNotPermittedDowngradesAMergingItemToAwaitingMerge() {
        var merging = item(SpecQueueItemStatus.MERGING, false);
        assertThat(runner.downgradeIfAutoMergeNotPermitted(projectId, merging.getId())).isTrue();
        assertThat(reload(merging).getStatus()).isEqualTo(SpecQueueItemStatus.AWAITING_MERGE);
        assertThat(eventCount(SpecQueueEventType.AUTO_MERGE_DOWNGRADED)).isEqualTo(1);
        assertThat(runner.downgradeIfAutoMergeNotPermitted(projectId, merging.getId())).isFalse();
    }

    @Test
    void autoMergeThatIsPermittedIsNotDowngraded() {
        allowAutoMerge();
        var merging = item(SpecQueueItemStatus.MERGING, true);
        assertThat(runner.downgradeIfAutoMergeNotPermitted(projectId, merging.getId())).isFalse();
        assertThat(reload(merging).getStatus()).isEqualTo(SpecQueueItemStatus.MERGING);
    }

    @Test
    void aMergeAttemptOnAnItemThatIsNotMergingIsStale() {
        var queued = item(SpecQueueItemStatus.QUEUED, false);
        assertThat(runner.claimMergeAttempt(projectId, queued.getId(), "sha1")).isEqualTo(MergeClaim.STALE);
    }

    @Test
    void aMergeAttemptWithoutPermissionDowngradesTheItem() {
        var notPermitted = item(SpecQueueItemStatus.MERGING, false);
        assertThat(runner.claimMergeAttempt(projectId, notPermitted.getId(), "sha1")).isEqualTo(MergeClaim.DOWNGRADED);
        assertThat(reload(notPermitted).getStatus()).isEqualTo(SpecQueueItemStatus.AWAITING_MERGE);
    }

    @Test
    void aPermittedMergeAttemptIsClaimedExactlyOnce() {
        allowAutoMerge();
        var permitted = item(SpecQueueItemStatus.MERGING, true);
        assertThat(runner.claimMergeAttempt(projectId, permitted.getId(), "sha1")).isEqualTo(MergeClaim.CLAIMED);
        assertThat(reload(permitted).getMergeAttemptStartedAt()).isNotNull();
        assertThat(runner.claimMergeAttempt(projectId, permitted.getId(), "sha1")).isEqualTo(MergeClaim.STALE);
    }

    @Test
    void completingAMergeOfAnItemThatIsNotMergingChangesNothing() {
        var queued = item(SpecQueueItemStatus.QUEUED, false);
        assertThat(runner.completeMerge(projectId, queued.getId(), "MERGED")).isFalse();
    }
}
