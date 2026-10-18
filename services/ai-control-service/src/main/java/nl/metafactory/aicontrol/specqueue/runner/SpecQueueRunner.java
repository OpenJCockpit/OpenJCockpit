package nl.metafactory.aicontrol.specqueue.runner;

import nl.metafactory.aicontrol.model.SpecQueueItemStatus;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * One tick: observe the active item of every project with open work, then start the next item. The tick holds
 * no transaction and no lock; every state change happens in a short transaction of the transitions class.
 */
@Component
public class SpecQueueRunner {

    private static final Logger log = LoggerFactory.getLogger(SpecQueueRunner.class);
    private static final Short ACTIVE_SLOT = 1;

    private final RunnerIdentityStatus identity;
    private final SpecQueueRunnerTransitions transitions;
    private final SpecQueueItemRepository items;
    private final RunnerStartStep startStep;
    private final RunnerObserveStep observeStep;
    private final RunnerMergeStep mergeStep;

    public SpecQueueRunner(RunnerIdentityStatus identity, SpecQueueRunnerTransitions transitions,
                           SpecQueueItemRepository items, RunnerStartStep startStep,
                           RunnerObserveStep observeStep, RunnerMergeStep mergeStep) {
        this.identity = identity;
        this.transitions = transitions;
        this.items = items;
        this.startStep = startStep;
        this.observeStep = observeStep;
        this.mergeStep = mergeStep;
    }

    public void tick() {
        if (!identity.isConfigured()) {
            identity.recordUnconfigured();
            return;
        }
        identity.recordConfigured();
        for (UUID projectId : transitions.projectIdsWithOpenWork()) {
            try {
                processProject(projectId);
            } catch (PessimisticLockingFailureException | OptimisticLockingFailureException e) {
                log.debug("QUEUE_BUSY projectId={}", projectId);
            } catch (RuntimeException e) {
                log.warn("spec-queue.project-failed projectId={} error={}", projectId, e.getClass().getSimpleName());
            }
        }
    }

    private void processProject(UUID projectId) {
        Optional<SpecQueueItem> active = items.findByProjectIdAndActiveSlot(projectId, ACTIVE_SLOT);
        active.ifPresent(this::observe);
        if (items.findByProjectIdAndActiveSlot(projectId, ACTIVE_SLOT).isEmpty()) {
            startStep.startNextItem(projectId);
        }
    }

    private void observe(SpecQueueItem item) {
        switch (item.getStatus()) {
            case STARTING -> startStep.expireStaleStart(item);
            case RUNNING -> observeStep.observeRunning(item);
            case AWAITING_MERGE -> mergeStep.pollAwaitingMerge(item);
            case MERGING -> mergeStep.driveMerging(item);
            default -> log.warn("unexpected-active-status projectId={} itemId={} status={}",
                    item.getProjectId(), item.getId(), item.getStatus());
        }
    }
}
