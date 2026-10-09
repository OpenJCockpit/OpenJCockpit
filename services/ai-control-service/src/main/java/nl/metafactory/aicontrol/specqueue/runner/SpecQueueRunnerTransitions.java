package nl.metafactory.aicontrol.specqueue.runner;

import nl.metafactory.aicontrol.config.SpecQueueProperties;
import nl.metafactory.aicontrol.model.SpecQueueFailureReason;
import nl.metafactory.aicontrol.model.SpecQueueItemStatus;
import nl.metafactory.aicontrol.model.SpecQueueState;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueTransitions;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueue;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueEvent;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueEventType;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItemPullRequest;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueItemPullRequestRepository;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueItemRepository;
import nl.metafactory.aicontrol.specqueue.planner.PlannerGuardrails;
import nl.metafactory.aicontrol.specqueue.planner.PlannerHistory;
import nl.metafactory.aicontrol.specqueue.planner.PlannerHistoryEntry;
import nl.metafactory.aicontrol.specqueue.planner.PlannerItemView;
import nl.metafactory.aicontrol.specqueue.planner.PlannerOverride;
import nl.metafactory.aicontrol.specqueue.planner.PlannerProjectView;
import nl.metafactory.aicontrol.specqueue.planner.QueuePlanner;
import nl.metafactory.aicontrol.specqueue.planner.RunFinishedDecision;
import nl.metafactory.aicontrol.specqueue.planner.RunOutcome;
import nl.metafactory.aicontrol.specqueue.planner.RunPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The short transactions of the runner. Each one takes the per-project queue lock first, re-loads the item
 * and verifies the expected status, and changes nothing when it no longer holds. No remote call happens in
 * here and database errors are never caught (PostgreSQL aborts the transaction after a failed statement).
 * Events carry actor SYSTEM and fixed codes only.
 */
@Component
public class SpecQueueRunnerTransitions {

    private static final Logger log = LoggerFactory.getLogger(SpecQueueRunnerTransitions.class);
    static final String ACTOR = "SYSTEM";
    private static final Short ACTIVE_SLOT = 1;
    private static final Set<SpecQueueItemStatus> OPEN = java.util.Arrays.stream(SpecQueueItemStatus.values())
            .filter(SpecQueueItemStatus::isOpen).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Set<SpecQueueItemStatus> FINISHED = EnumSet.of(SpecQueueItemStatus.MERGED,
            SpecQueueItemStatus.COMPLETED_NO_CHANGES, SpecQueueItemStatus.FAILED, SpecQueueItemStatus.SKIPPED,
            SpecQueueItemStatus.CANCELLED);

    public record StartClaim(UUID projectId, UUID itemId, String workflowId, String specFile) {}

    public enum MergeClaim { CLAIMED, STALE, DOWNGRADED }

    public enum PrObservation { MERGED, OPEN, CLOSED_UNMERGED }

    private final SpecQueueTransitions transitions;
    private final SpecQueueItemRepository items;
    private final SpecQueueItemPullRequestRepository pullRequests;
    private final ProjectRepository projects;
    private final QueuePlanner planner;
    private final RunOutcomeClassifier classifier;
    private final SpecQueueProperties.Runner config;
    private final Clock clock;

    public SpecQueueRunnerTransitions(SpecQueueTransitions transitions, SpecQueueItemRepository items,
                                      SpecQueueItemPullRequestRepository pullRequests, ProjectRepository projects,
                                      QueuePlanner planner, RunOutcomeClassifier classifier,
                                      SpecQueueProperties properties, ObjectProvider<Clock> clock) {
        this.transitions = transitions;
        this.items = items;
        this.pullRequests = pullRequests;
        this.projects = projects;
        this.planner = planner;
        this.classifier = classifier;
        this.config = properties.getRunner();
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    /** Missing timestamp counts as elapsed; the boundary instant counts as elapsed. */
    public static boolean isLeaseElapsed(Instant claimedAt, Duration lease, Instant now) {
        return claimedAt == null || !now.isBefore(claimedAt.plus(lease));
    }

    // ── reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UUID> projectIdsWithOpenWork() {
        return items.findProjectIdsByStatusIn(OPEN);
    }

    @Transactional(readOnly = true)
    public List<SpecQueueItemPullRequest> currentRunPullRequests(SpecQueueItem item) {
        return pullRequests.findByItemIdOrderByCreatedAtAsc(item.getId()).stream()
                .filter(pr -> pr.getWorkflowRunId().equals(item.getWorkflowRunId()))
                .toList();
    }

    // ── start ────────────────────────────────────────────────────────────────

    @Transactional
    public Optional<StartClaim> claimNextItem(UUID projectId) {
        SpecQueue queue = transitions.lockQueue(projectId);
        var project = projects.findById(projectId);
        if (project.isEmpty() || project.get().getActive() != 1 || queue.getState() != SpecQueueState.ACTIVE
                || items.findByProjectIdAndActiveSlot(projectId, ACTIVE_SLOT).isPresent()) {
            return Optional.empty();
        }
        List<SpecQueueItem> queued = items.findByProjectIdAndStatusInOrderByPositionAsc(
                projectId, EnumSet.of(SpecQueueItemStatus.QUEUED));
        if (queued.isEmpty()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        var views = queued.stream().map(i -> view(i, queue)).toList();
        var finished = items.findByProjectIdAndStatusInOrderByPositionAsc(projectId, FINISHED);
        var recent = finished.subList(Math.max(0, finished.size() - config.getRecentlyFinishedLimit()), finished.size());
        var history = new PlannerHistory(recent.stream()
                .map(i -> new PlannerHistoryEntry(i.getStatus(), i.getFailureReason())).toList());
        var selection = PlannerGuardrails.selectNext(planner, new PlannerProjectView(projectId, queue.getState(),
                queue.isAutoMergeAllowed()), views, history);
        String override = selection.override().map(Enum::name).orElse(null);
        if (selection.itemId().isEmpty()) {
            String code = override != null ? override : "NONE";
            if (!code.equals(queue.getLastPlannerDecision())) {
                queue.rememberPlannerDecision(code, now);
                record(new SpecQueueEvent(projectId, null, SpecQueueEventType.SELECTED, ACTOR, now)
                        .withPlanner("NONE", planner.version(), override));
            }
            return Optional.empty();
        }
        SpecQueueItem item = queued.stream().filter(i -> i.getId().equals(selection.itemId().get())).findFirst().orElseThrow();
        item.transitionTo(SpecQueueItemStatus.STARTING, now);
        item.recordStartClaim(now);
        items.saveAndFlush(item);
        if (queue.getLastPlannerDecision() != null) {
            queue.rememberPlannerDecision(null, now);
        }
        record(new SpecQueueEvent(projectId, item.getId(), SpecQueueEventType.SELECTED, ACTOR, now)
                .withStatusChange(SpecQueueItemStatus.QUEUED, SpecQueueItemStatus.STARTING)
                .withPlanner("SELECT", planner.version(), override)
                .withWorkflow(item.getWorkflowId(), null));
        return Optional.of(new StartClaim(projectId, item.getId(), item.getWorkflowId(), item.getSpecFile()));
    }

    @Transactional
    public void expireStartIfLeaseElapsed(UUID projectId, UUID itemId) {
        SpecQueue queue = transitions.lockQueue(projectId);
        loadInStatus(projectId, itemId, SpecQueueItemStatus.STARTING)
                .filter(i -> isLeaseElapsed(i.getStartClaimedAt(), config.getStartLease(), clock.instant()))
                .ifPresent(i -> failAndHalt(queue, i, SpecQueueFailureReason.START_OUTCOME_UNKNOWN,
                        SpecQueueEventType.FAILED, null));
    }

    /** The start provably never reached the agent service: back to QUEUED, keeping its position. */
    @Transactional
    public void revertStart(UUID projectId, UUID itemId, String pollErrorCode) {
        SpecQueue queue = transitions.lockQueue(projectId);
        loadInStatus(projectId, itemId, SpecQueueItemStatus.STARTING).ifPresent(item -> {
            Instant now = clock.instant();
            item.transitionTo(SpecQueueItemStatus.QUEUED, now);
            item.recordStartClaim(null);
            items.saveAndFlush(item);
            if (pollErrorCode != null) {
                queue.recordPollError(pollErrorCode, now);
            }
            record(new SpecQueueEvent(projectId, itemId, SpecQueueEventType.START_REVERTED, ACTOR, now)
                    .withStatusChange(SpecQueueItemStatus.STARTING, SpecQueueItemStatus.QUEUED)
                    .withReasonCode(pollErrorCode));
        });
    }

    @Transactional
    public void failStart(UUID projectId, UUID itemId, SpecQueueFailureReason reason, SpecQueueEventType eventType) {
        SpecQueue queue = transitions.lockQueue(projectId);
        loadInStatus(projectId, itemId, SpecQueueItemStatus.STARTING)
                .ifPresent(item -> failAndHalt(queue, item, reason, eventType, null));
    }

    /** Compare-and-set STARTING to RUNNING. False when the item is no longer STARTING. */
    @Transactional
    public boolean recordStarted(UUID projectId, UUID itemId, String runId, String status, Instant startedAt) {
        transitions.lockQueue(projectId);
        Optional<SpecQueueItem> found = loadInStatus(projectId, itemId, SpecQueueItemStatus.STARTING);
        if (found.isEmpty()) {
            return false;
        }
        SpecQueueItem item = found.get();
        Instant now = clock.instant();
        item.transitionTo(SpecQueueItemStatus.RUNNING, now);
        item.recordRun(runId, startedAt != null ? startedAt : now);
        item.recordLastRunStatus(classifier.normalizeStatus(status), now);
        item.recordStartClaim(null);
        items.saveAndFlush(item);
        record(new SpecQueueEvent(projectId, itemId, SpecQueueEventType.STARTED, ACTOR, now)
                .withStatusChange(SpecQueueItemStatus.STARTING, SpecQueueItemStatus.RUNNING)
                .withWorkflow(item.getWorkflowId(), runId));
        return true;
    }

    /** The one write without the queue lock: events are append-only. */
    @Transactional
    public void recordOrphanRun(UUID projectId, UUID itemId, String workflowId, String runId) {
        record(new SpecQueueEvent(projectId, itemId, SpecQueueEventType.ORPHAN_RUN_DETECTED, ACTOR, clock.instant())
                .withWorkflow(workflowId, runId));
    }

    // ── observe ──────────────────────────────────────────────────────────────

    @Transactional
    public void recordPollError(UUID projectId, String code) {
        transitions.lockQueue(projectId).recordPollError(code, clock.instant());
    }

    @Transactional
    public void recordPollSuccess(UUID projectId) {
        transitions.lockQueue(projectId).recordPollSuccess(clock.instant());
    }

    @Transactional
    public boolean recordObservation(UUID projectId, UUID itemId, String normalizedStatus) {
        SpecQueue queue = transitions.lockQueue(projectId);
        Optional<SpecQueueItem> found = loadInStatus(projectId, itemId, SpecQueueItemStatus.RUNNING);
        if (found.isEmpty()) {
            return false;
        }
        Instant now = clock.instant();
        SpecQueueItem item = found.get();
        if (!normalizedStatus.equals(item.getLastRunStatus())) {
            item.recordLastRunStatus(normalizedStatus, now);
            items.saveAndFlush(item);
        }
        queue.recordPollSuccess(now);
        return true;
    }

    /** Same effect as the user cancel flow, so whichever runs first leaves the other a no-op. */
    @Transactional
    public boolean cancelRunByQueue(UUID projectId, UUID itemId) {
        SpecQueue queue = transitions.lockQueue(projectId);
        Optional<SpecQueueItem> found = loadInStatus(projectId, itemId, SpecQueueItemStatus.RUNNING);
        if (found.isEmpty()) {
            return false;
        }
        SpecQueueItem item = found.get();
        Instant now = clock.instant();
        item.transitionTo(SpecQueueItemStatus.CANCELLED, now);
        item.recordFinished(now);
        items.saveAndFlush(item);
        record(new SpecQueueEvent(projectId, itemId, SpecQueueEventType.CANCELLED, ACTOR, now)
                .withStatusChange(SpecQueueItemStatus.RUNNING, SpecQueueItemStatus.CANCELLED)
                .withReasonCode("CANCEL_REQUESTED")
                .withWorkflow(item.getWorkflowId(), item.getWorkflowRunId()));
        if (queue.getState() == SpecQueueState.ACTIVE) {
            queue.changeState(SpecQueueState.PAUSED, now);
            record(new SpecQueueEvent(projectId, null, SpecQueueEventType.PAUSED, ACTOR, now)
                    .withReasonCode("ITEM_CANCELLED"));
        }
        return true;
    }

    @Transactional
    public boolean finishRun(UUID projectId, UUID itemId, RunOutcome outcome,
                             RunOutcomeClassifier.RunFeatures features, String normalizedStatus) {
        if (outcome instanceof RunOutcome.NonTerminal || outcome instanceof RunOutcome.CancelledByQueue) {
            throw new IllegalArgumentException("Outcome is not a finished run: " + outcome.getClass().getSimpleName());
        }
        SpecQueue queue = transitions.lockQueue(projectId);
        Optional<SpecQueueItem> found = loadInStatus(projectId, itemId, SpecQueueItemStatus.RUNNING);
        if (found.isEmpty()) {
            return false;
        }
        SpecQueueItem item = found.get();
        Instant now = clock.instant();
        var decision = PlannerGuardrails.decideAfterRun(planner, view(item, queue), outcome);
        String override = decision.override().map(PlannerOverride::name).orElse(null);
        item.recordLastRunStatus(normalizedStatus, now);
        int prCount = outcome.pullRequests().size();

        if (outcome instanceof RunOutcome.Failed failed) {
            record(runFinished(item, SpecQueueItemStatus.FAILED, failed.reason().name(), decision, override, features, 0, now));
            failAndHalt(queue, item, failed.reason(), SpecQueueEventType.FAILED, null);
            return true;
        }
        if (outcome instanceof RunOutcome.SucceededNoChanges) {
            item.transitionTo(SpecQueueItemStatus.COMPLETED_NO_CHANGES, now);
            item.recordFinished(now);
            items.saveAndFlush(item);
            record(runFinished(item, SpecQueueItemStatus.COMPLETED_NO_CHANGES, null, decision, override, features, 0, now));
            record(new SpecQueueEvent(projectId, itemId, SpecQueueEventType.COMPLETED_NO_CHANGES, ACTOR, now)
                    .withStatusChange(SpecQueueItemStatus.RUNNING, SpecQueueItemStatus.COMPLETED_NO_CHANGES)
                    .withWorkflow(item.getWorkflowId(), item.getWorkflowRunId()));
        } else {
            var existing = pullRequests.findByItemIdOrderByCreatedAtAsc(itemId);
            for (RunPullRequest pr : outcome.pullRequests()) {
                boolean known = existing.stream().anyMatch(e -> e.getWorkflowRunId().equals(item.getWorkflowRunId())
                        && e.getUrl().equalsIgnoreCase(pr.url()));
                if (!known) {
                    pullRequests.save(new SpecQueueItemPullRequest(itemId, item.getWorkflowRunId(), pr.url(),
                            pr.owner(), pr.repo(), pr.number(), now));
                }
            }
            SpecQueueItemStatus target = decision.decision() == RunFinishedDecision.MERGE_AND_CONTINUE
                    ? SpecQueueItemStatus.MERGING : SpecQueueItemStatus.AWAITING_MERGE;
            item.transitionTo(target, now);
            items.saveAndFlush(item);
            record(runFinished(item, target, null, decision, override, features, prCount, now));
            if (target == SpecQueueItemStatus.AWAITING_MERGE) {
                record(new SpecQueueEvent(projectId, itemId, SpecQueueEventType.AWAITING_MERGE, ACTOR, now)
                        .withStatusChange(SpecQueueItemStatus.RUNNING, SpecQueueItemStatus.AWAITING_MERGE)
                        .withWorkflow(item.getWorkflowId(), item.getWorkflowRunId())
                        .withPublication(null, null, prCount));
            }
        }
        if (decision.decision() == RunFinishedDecision.HALT) {
            halt(queue, "PLANNER_HALT", now);
        }
        return true;
    }

    // ── await merge / merge ──────────────────────────────────────────────────

    @Transactional
    public boolean failActiveItem(UUID projectId, UUID itemId, SpecQueueItemStatus expected,
                                  SpecQueueFailureReason reason, String mergeResult) {
        SpecQueue queue = transitions.lockQueue(projectId);
        Optional<SpecQueueItem> found = loadInStatus(projectId, itemId, expected);
        found.ifPresent(item -> failAndHalt(queue, item, reason, SpecQueueEventType.FAILED, mergeResult));
        return found.isPresent();
    }

    @Transactional
    public boolean applyPullRequestObservations(UUID projectId, UUID itemId, Map<UUID, PrObservation> observations) {
        SpecQueue queue = transitions.lockQueue(projectId);
        Optional<SpecQueueItem> found = loadInStatus(projectId, itemId, SpecQueueItemStatus.AWAITING_MERGE);
        if (found.isEmpty()) {
            return false;
        }
        SpecQueueItem item = found.get();
        Instant now = clock.instant();
        List<SpecQueueItemPullRequest> current = currentRunPullRequests(item);
        boolean closed = false;
        for (SpecQueueItemPullRequest pr : current) {
            PrObservation observation = observations.get(pr.getId());
            if (observation == PrObservation.MERGED && pr.getMergedAt() == null) {
                pr.markMerged(now);
            } else if (observation == PrObservation.CLOSED_UNMERGED) {
                pr.markClosedUnmerged(now);
                closed = true;
            }
        }
        pullRequests.saveAll(current);
        if (closed) {
            failAndHalt(queue, item, SpecQueueFailureReason.PR_CLOSED_UNMERGED, SpecQueueEventType.FAILED, null);
        } else if (!current.isEmpty() && current.stream().allMatch(pr -> pr.getMergedAt() != null)) {
            markMerged(item, SpecQueueItemStatus.AWAITING_MERGE, "MERGED_BY_HUMAN", current.size(), now);
        } else {
            queue.recordPollSuccess(now);
        }
        return true;
    }

    @Transactional
    public boolean downgradeIfAutoMergeNotPermitted(UUID projectId, UUID itemId) {
        SpecQueue queue = transitions.lockQueue(projectId);
        return loadInStatus(projectId, itemId, SpecQueueItemStatus.MERGING)
                .filter(i -> i.getMergeAttemptStartedAt() == null)
                .filter(i -> !(i.isAutoMerge() && queue.isAutoMergeAllowed()))
                .map(i -> {
                    downgrade(i);
                    return true;
                }).orElse(false);
    }

    /** Commits the attempt BEFORE the merge call so a crash can never lead to a second merge. */
    @Transactional
    public MergeClaim claimMergeAttempt(UUID projectId, UUID itemId, String headSha) {
        SpecQueue queue = transitions.lockQueue(projectId);
        Optional<SpecQueueItem> found = loadInStatus(projectId, itemId, SpecQueueItemStatus.MERGING)
                .filter(i -> i.getMergeAttemptStartedAt() == null);
        if (found.isEmpty()) {
            return MergeClaim.STALE;
        }
        SpecQueueItem item = found.get();
        if (!(item.isAutoMerge() && queue.isAutoMergeAllowed())) {
            downgrade(item);
            return MergeClaim.DOWNGRADED;
        }
        Instant now = clock.instant();
        item.recordMergeAttempt(now, headSha);
        items.saveAndFlush(item);
        record(new SpecQueueEvent(projectId, itemId, SpecQueueEventType.MERGE_ATTEMPTED, ACTOR, now)
                .withStatusChange(SpecQueueItemStatus.MERGING, SpecQueueItemStatus.MERGING)
                .withWorkflow(item.getWorkflowId(), item.getWorkflowRunId()));
        return MergeClaim.CLAIMED;
    }

    @Transactional
    public boolean completeMerge(UUID projectId, UUID itemId, String mergeResult) {
        transitions.lockQueue(projectId);
        Optional<SpecQueueItem> found = loadInStatus(projectId, itemId, SpecQueueItemStatus.MERGING);
        if (found.isEmpty()) {
            return false;
        }
        SpecQueueItem item = found.get();
        Instant now = clock.instant();
        List<SpecQueueItemPullRequest> current = currentRunPullRequests(item);
        current.stream().filter(pr -> pr.getMergedAt() == null).forEach(pr -> pr.markMerged(now));
        pullRequests.saveAll(current);
        markMerged(item, SpecQueueItemStatus.MERGING, mergeResult, current.size(), now);
        return true;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Optional<SpecQueueItem> loadInStatus(UUID projectId, UUID itemId, SpecQueueItemStatus expected) {
        return items.findByIdAndProjectId(itemId, projectId).filter(i -> i.getStatus() == expected);
    }

    private static PlannerItemView view(SpecQueueItem item, SpecQueue queue) {
        return new PlannerItemView(item.getId(), item.getSpecFile(), item.getWorkflowId(), (int) item.getPosition(),
                item.isAutoMerge(), item.isAutoMerge() && queue.isAutoMergeAllowed());
    }

    private SpecQueueEvent runFinished(SpecQueueItem item, SpecQueueItemStatus to, String reason,
                                       PlannerGuardrails.Decision decision, String override,
                                       RunOutcomeClassifier.RunFeatures features, int prCount, Instant now) {
        var event = new SpecQueueEvent(item.getProjectId(), item.getId(), SpecQueueEventType.RUN_FINISHED, ACTOR, now)
                .withStatusChange(SpecQueueItemStatus.RUNNING, to)
                .withReasonCode(reason)
                .withWorkflow(item.getWorkflowId(), item.getWorkflowRunId())
                .withPlanner(decision.decision().name(), planner.version(), override)
                .withPublication(null, null, prCount);
        if (features != null) {
            event.withRunMetrics(features.durationMs(), features.reviewIterations(),
                    features.approvalGateIterations(), features.approvalGateOutcome());
        }
        return event;
    }

    private void downgrade(SpecQueueItem item) {
        Instant now = clock.instant();
        item.transitionTo(SpecQueueItemStatus.AWAITING_MERGE, now);
        items.saveAndFlush(item);
        record(new SpecQueueEvent(item.getProjectId(), item.getId(), SpecQueueEventType.AUTO_MERGE_DOWNGRADED, ACTOR, now)
                .withStatusChange(SpecQueueItemStatus.MERGING, SpecQueueItemStatus.AWAITING_MERGE)
                .withReasonCode("AUTO_MERGE_NOT_PERMITTED")
                .withWorkflow(item.getWorkflowId(), item.getWorkflowRunId()));
    }

    private void markMerged(SpecQueueItem item, SpecQueueItemStatus from, String mergeResult, int prCount, Instant now) {
        item.transitionTo(SpecQueueItemStatus.MERGED, now);
        item.recordFinished(now);
        items.saveAndFlush(item);
        record(new SpecQueueEvent(item.getProjectId(), item.getId(), SpecQueueEventType.MERGED, ACTOR, now)
                .withStatusChange(from, SpecQueueItemStatus.MERGED)
                .withWorkflow(item.getWorkflowId(), item.getWorkflowRunId())
                .withPublication(mergeResult, null, prCount));
    }

    private void failAndHalt(SpecQueue queue, SpecQueueItem item, SpecQueueFailureReason reason,
                             SpecQueueEventType eventType, String mergeResult) {
        Instant now = clock.instant();
        SpecQueueItemStatus from = item.getStatus();
        item.transitionTo(SpecQueueItemStatus.FAILED, now);
        item.recordFailureReason(reason, now);
        item.recordFinished(now);
        items.saveAndFlush(item);
        var event = new SpecQueueEvent(item.getProjectId(), item.getId(), eventType, ACTOR, now)
                .withStatusChange(from, SpecQueueItemStatus.FAILED)
                .withReasonCode(reason.name())
                .withWorkflow(item.getWorkflowId(), item.getWorkflowRunId());
        if (mergeResult != null) {
            event.withPublication(mergeResult, null, null);
        }
        record(event);
        halt(queue, reason.name(), now);
    }

    private void halt(SpecQueue queue, String reasonCode, Instant now) {
        if (queue.getState() != SpecQueueState.HALTED) {
            queue.changeState(SpecQueueState.HALTED, now);
            record(new SpecQueueEvent(queue.getProjectId(), null, SpecQueueEventType.HALTED, ACTOR, now)
                    .withReasonCode(reasonCode));
        }
    }

    /** Appends the audit event and logs ids and enums only. */
    private void record(SpecQueueEvent event) {
        transitions.appendEvent(event);
        log.info("spec-queue.transition projectId={} itemId={} runId={} from={} to={} reason={} decision={}",
                event.getProjectId(), dash(event.getItemId()), dash(event.getWorkflowRunId()),
                dash(event.getFromStatus()), dash(event.getToStatus()), dash(event.getReasonCode()),
                dash(event.getPlannerDecision()));
    }

    private static String dash(Object value) {
        return value == null ? "-" : value.toString();
    }
}
