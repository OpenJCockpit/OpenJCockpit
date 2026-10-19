package nl.metafactory.aicontrol.specqueue.app;

import nl.metafactory.aicontrol.config.SpecQueueProperties;
import nl.metafactory.aicontrol.model.SpecQueueEnqueueRequest;
import nl.metafactory.aicontrol.model.SpecQueueItemStatus;
import nl.metafactory.aicontrol.model.SpecQueueItemUpdateRequest;
import nl.metafactory.aicontrol.model.SpecQueueState;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueException.Code;
import nl.metafactory.aicontrol.specqueue.app.WorkflowBindingValidator.ValidatedWorkflow;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueue;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueEvent;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueEventType;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Short database transactions behind the user-facing queue operations. Every method takes the
 * per-project queue lock first. No remote I/O happens in here.
 */
@Component
public class SpecQueueUserTransitions {

    private static final Logger log = LoggerFactory.getLogger(SpecQueueUserTransitions.class);

    private static final Set<SpecQueueItemStatus> OPEN = EnumSet.of(
            SpecQueueItemStatus.QUEUED, SpecQueueItemStatus.STARTING, SpecQueueItemStatus.RUNNING,
            SpecQueueItemStatus.AWAITING_MERGE, SpecQueueItemStatus.MERGING);

    private final SpecQueueTransitions transitions;
    private final SpecQueueItemRepository items;
    private final SpecQueueProperties properties;
    private final Clock clock;

    public SpecQueueUserTransitions(SpecQueueTransitions transitions, SpecQueueItemRepository items,
                                    SpecQueueProperties properties, ObjectProvider<Clock> clock) {
        this.transitions = transitions;
        this.items = items;
        this.properties = properties;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    @Transactional
    public SpecQueueItem appendItem(UUID projectId, SpecQueueEnqueueRequest request, ValidatedWorkflow workflow,
                                    int specFileSizeBytes, CurrentActor actor) {
        SpecQueue queue = transitions.lockQueue(projectId);
        if (request.autoMerge() && !queue.isAutoMergeAllowed()) {
            throw new SpecQueueException(Code.AUTO_MERGE_NOT_ALLOWED,
                    "Auto-merge is not allowed for this project");
        }
        if (items.findByProjectIdAndOpenSpecKey(projectId, request.specFile()).isPresent()) {
            throw new SpecQueueException(Code.DUPLICATE_SPEC_FILE,
                    "This spec file is already queued for the project");
        }
        int open = items.findByProjectIdAndStatusInOrderByPositionAsc(projectId, OPEN).size();
        if (open >= properties.getRunner().getMaxQueuedItems()) {
            throw new SpecQueueException(Code.QUEUE_FULL, "The spec queue is full");
        }
        Instant now = clock.instant();
        long position = items.findFirstByProjectIdOrderByPositionDesc(projectId)
                .map(i -> i.getPosition() + 1).orElse(1L);
        SpecQueueItem item = new SpecQueueItem(projectId, request.specFile(), workflow.workflowId(),
                workflow.workflowName(), request.autoMerge(), position, specFileSizeBytes, actor.subject(),
                actor.displayName(), now);
        items.saveAndFlush(item);
        record(new SpecQueueEvent(projectId, item.getId(), SpecQueueEventType.ENQUEUED, actor.eventLabel(), now)
                .withStatusChange(null, SpecQueueItemStatus.QUEUED)
                .withWorkflow(item.getWorkflowId(), null));
        return item;
    }

    @Transactional
    public SpecQueueItem updateItem(UUID projectId, UUID itemId, SpecQueueItemUpdateRequest request,
                                    ValidatedWorkflow workflow, CurrentActor actor) {
        SpecQueue queue = transitions.lockQueue(projectId);
        SpecQueueItem item = requireItem(projectId, itemId);
        if (item.getStatus() != SpecQueueItemStatus.QUEUED) {
            throw new SpecQueueException(Code.ITEM_NOT_EDITABLE, "Only queued items can be edited");
        }
        boolean autoMerge = request.autoMerge() != null ? request.autoMerge() : item.isAutoMerge();
        if (autoMerge && !item.isAutoMerge() && !queue.isAutoMergeAllowed()) {
            throw new SpecQueueException(Code.AUTO_MERGE_NOT_ALLOWED,
                    "Auto-merge is not allowed for this project");
        }
        String workflowId = workflow != null ? workflow.workflowId() : item.getWorkflowId();
        String workflowName = workflow != null ? workflow.workflowName() : item.getWorkflowName();
        Instant now = clock.instant();
        item.reassign(item.getSpecFile(), workflowId, workflowName, autoMerge, item.getSpecFileSizeBytes(), now);
        items.saveAndFlush(item);
        record(new SpecQueueEvent(projectId, itemId, SpecQueueEventType.UPDATED, actor.eventLabel(), now)
                .withWorkflow(workflowId, null));
        return item;
    }

    @Transactional
    public void reorder(UUID projectId, List<UUID> itemIds, CurrentActor actor) {
        transitions.lockQueue(projectId);
        List<SpecQueueItem> queued = items.findByProjectIdAndStatusInOrderByPositionAsc(
                projectId, List.of(SpecQueueItemStatus.QUEUED));
        Set<UUID> queuedIds = queued.stream().map(SpecQueueItem::getId).collect(Collectors.toSet());
        if (itemIds.size() != queued.size() || !queuedIds.equals(new HashSet<>(itemIds))) {
            throw new SpecQueueException(Code.STALE_ORDER,
                    "The queue changed; reload it and try again");
        }
        if (queued.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        List<Long> sortedPositions = queued.stream().map(SpecQueueItem::getPosition).sorted().toList();
        long max = items.findFirstByProjectIdOrderByPositionDesc(projectId)
                .map(SpecQueueItem::getPosition).orElse(0L);
        // Two phases: park the items above every existing position, then assign the final slots,
        // so UNIQUE(project_id, position) is never violated mid-way.
        List<SpecQueueItem> ordered = new ArrayList<>();
        for (UUID id : itemIds) {
            ordered.add(queued.stream().filter(i -> i.getId().equals(id)).findFirst().orElseThrow());
        }
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).moveTo(max + 1 + i, now);
        }
        items.flush();
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).moveTo(sortedPositions.get(i), now);
        }
        items.flush();
        record(new SpecQueueEvent(projectId, null, SpecQueueEventType.REORDERED, actor.eventLabel(), now));
    }

    @Transactional
    public SpecQueueItem removeOrCancelIdleItem(UUID projectId, UUID itemId, CurrentActor actor) {
        SpecQueue queue = transitions.lockQueue(projectId);
        SpecQueueItem item = requireItem(projectId, itemId);
        return switch (item.getStatus()) {
            case QUEUED, FAILED -> finishItem(item, SpecQueueItemStatus.REMOVED, SpecQueueEventType.REMOVED, actor);
            case AWAITING_MERGE -> cancelItemAndPause(queue, item, actor);
            case MERGING -> {
                if (item.getMergeAttemptStartedAt() != null) {
                    throw new SpecQueueException(Code.ITEM_BUSY, "A merge attempt is in progress");
                }
                yield cancelItemAndPause(queue, item, actor);
            }
            case STARTING, RUNNING -> throw new SpecQueueException(Code.ITEM_BUSY, "The item is being processed");
            default -> throw new SpecQueueException(Code.ITEM_NOT_REMOVABLE, "The item is already finished");
        };
    }

    /** Marks a running item as cancel-requested and returns its run id (to be stopped outside the transaction). */
    @Transactional
    public String beginCancel(UUID projectId, UUID itemId) {
        transitions.lockQueue(projectId);
        SpecQueueItem item = requireItem(projectId, itemId);
        String runId = item.getWorkflowRunId();
        if (item.getStatus() != SpecQueueItemStatus.RUNNING || runId == null || runId.isBlank()) {
            throw new SpecQueueException(Code.ITEM_BUSY, "The item cannot be cancelled right now");
        }
        item.recordCancelRequest(clock.instant());
        items.saveAndFlush(item);
        return runId;
    }

    @Transactional
    public void abortCancel(UUID projectId, UUID itemId) {
        transitions.lockQueue(projectId);
        items.findByIdAndProjectId(itemId, projectId).ifPresent(item -> {
            item.recordCancelRequest(null);
            items.saveAndFlush(item);
        });
    }

    @Transactional
    public SpecQueueItem completeCancel(UUID projectId, UUID itemId, CurrentActor actor) {
        SpecQueue queue = transitions.lockQueue(projectId);
        SpecQueueItem item = requireItem(projectId, itemId);
        if (item.getStatus() == SpecQueueItemStatus.RUNNING) {
            return cancelItemAndPause(queue, item, actor);
        }
        item.recordCancelRequest(null);
        return items.saveAndFlush(item);
    }

    @Transactional
    public void pause(UUID projectId, CurrentActor actor) {
        SpecQueue queue = transitions.lockQueue(projectId);
        if (queue.getState() == SpecQueueState.HALTED) {
            throw new SpecQueueException(Code.QUEUE_HALTED, "The queue is halted; resume it after resolving the failed item");
        }
        if (queue.getState() == SpecQueueState.PAUSED) {
            return;
        }
        Instant now = clock.instant();
        queue.changeState(SpecQueueState.PAUSED, now);
        record(new SpecQueueEvent(projectId, null, SpecQueueEventType.PAUSED, actor.eventLabel(), now)
                .withReasonCode("USER_REQUEST"));
    }

    @Transactional
    public void resume(UUID projectId, CurrentActor actor) {
        SpecQueue queue = transitions.lockQueue(projectId);
        if (queue.getState() == SpecQueueState.ACTIVE) {
            return;
        }
        boolean hasFailed = !items.findByProjectIdAndStatusInOrderByPositionAsc(
                projectId, List.of(SpecQueueItemStatus.FAILED)).isEmpty();
        if (hasFailed) {
            throw new SpecQueueException(Code.UNRESOLVED_FAILED_ITEM,
                    "Retry, skip or remove the failed item before resuming");
        }
        Instant now = clock.instant();
        queue.changeState(SpecQueueState.ACTIVE, now);
        record(new SpecQueueEvent(projectId, null, SpecQueueEventType.RESUMED, actor.eventLabel(), now));
    }

    @Transactional
    public SpecQueueItem retry(UUID projectId, UUID itemId, CurrentActor actor) {
        transitions.lockQueue(projectId);
        SpecQueueItem item = requireItem(projectId, itemId);
        if (item.getStatus() != SpecQueueItemStatus.FAILED) {
            throw new SpecQueueException(Code.ITEM_NOT_FAILED, "Only failed items can be retried");
        }
        Instant now = clock.instant();
        String previousReason = item.getFailureReason() != null ? item.getFailureReason().name() : null;
        String previousRun = item.getWorkflowRunId();
        long front = items.findFirstByProjectIdOrderByPositionAsc(projectId)
                .map(i -> i.getPosition() - 1).orElse(1L);
        if (front < 1) {
            renumberWithFirst(projectId, item, now);
        } else {
            item.moveTo(front, now);
        }
        item.transitionTo(SpecQueueItemStatus.QUEUED, now);
        item.recordFailureReason(null, now);
        item.recordRun(null, null);
        item.recordLastRunStatus(null, now);
        item.recordStartClaim(null);
        item.recordMergeAttempt(null, null);
        item.recordCancelRequest(null);
        item.recordFinished(null);
        items.saveAndFlush(item);
        record(new SpecQueueEvent(projectId, itemId, SpecQueueEventType.RETRIED, actor.eventLabel(), now)
                .withStatusChange(SpecQueueItemStatus.FAILED, SpecQueueItemStatus.QUEUED)
                .withReasonCode(previousReason)
                .withWorkflow(item.getWorkflowId(), previousRun));
        return item;
    }

    /** Keeps positions positive: renumbers the project's items 1..n with {@code first} at the front. */
    private void renumberWithFirst(UUID projectId, SpecQueueItem first, Instant now) {
        List<SpecQueueItem> ordered = new ArrayList<>();
        ordered.add(first);
        items.findByProjectIdOrderByPositionAsc(projectId).stream()
                .filter(i -> !i.getId().equals(first.getId())).forEach(ordered::add);
        long max = ordered.stream().mapToLong(SpecQueueItem::getPosition).max().orElse(0L);
        // Two phases, as in reorder, so UNIQUE(project_id, position) is never violated mid-way.
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).moveTo(max + 1 + i, now);
        }
        items.flush();
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).moveTo(i + 1L, now);
        }
        items.flush();
    }

    @Transactional
    public SpecQueueItem skip(UUID projectId, UUID itemId, CurrentActor actor) {
        transitions.lockQueue(projectId);
        SpecQueueItem item = requireItem(projectId, itemId);
        if (item.getStatus() != SpecQueueItemStatus.FAILED) {
            throw new SpecQueueException(Code.ITEM_NOT_FAILED, "Only failed items can be skipped");
        }
        return finishItem(item, SpecQueueItemStatus.SKIPPED, SpecQueueEventType.SKIPPED, actor);
    }

    @Transactional
    public SpecQueue changeAutoMergeAllowed(UUID projectId, boolean allowed, CurrentActor actor) {
        SpecQueue queue = transitions.lockQueue(projectId);
        if (queue.isAutoMergeAllowed() == allowed) {
            return queue;
        }
        Instant now = clock.instant();
        queue.changeAutoMergeAllowed(allowed, actor.eventLabel(), now);
        record(new SpecQueueEvent(projectId, null, SpecQueueEventType.SETTING_CHANGED, actor.eventLabel(), now)
                .withReasonCode(allowed ? "AUTO_MERGE_ALLOWED_ON" : "AUTO_MERGE_ALLOWED_OFF"));
        return queue;
    }

    private SpecQueueItem requireItem(UUID projectId, UUID itemId) {
        return items.findByIdAndProjectId(itemId, projectId)
                .orElseThrow(() -> new SpecQueueException(Code.ITEM_NOT_FOUND, "Queue item not found"));
    }

    private SpecQueueItem finishItem(SpecQueueItem item, SpecQueueItemStatus finalStatus,
                                     SpecQueueEventType eventType, CurrentActor actor) {
        Instant now = clock.instant();
        SpecQueueItemStatus from = item.getStatus();
        item.transitionTo(finalStatus, now);
        item.recordFinished(now);
        items.saveAndFlush(item);
        record(new SpecQueueEvent(item.getProjectId(), item.getId(), eventType, actor.eventLabel(), now)
                .withStatusChange(from, finalStatus)
                .withReasonCode(item.getFailureReason() != null ? item.getFailureReason().name() : null)
                .withWorkflow(item.getWorkflowId(), item.getWorkflowRunId()));
        return item;
    }

    private SpecQueueItem cancelItemAndPause(SpecQueue queue, SpecQueueItem item, CurrentActor actor) {
        SpecQueueItem cancelled = finishItem(item, SpecQueueItemStatus.CANCELLED, SpecQueueEventType.CANCELLED, actor);
        if (queue.getState() == SpecQueueState.ACTIVE) {
            Instant now = clock.instant();
            queue.changeState(SpecQueueState.PAUSED, now);
            record(new SpecQueueEvent(queue.getProjectId(), null, SpecQueueEventType.PAUSED, actor.eventLabel(), now)
                    .withReasonCode("ITEM_CANCELLED"));
        }
        return cancelled;
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
