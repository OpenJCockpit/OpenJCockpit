package nl.metafactory.aicontrol.specqueue.app;

import nl.metafactory.aicontrol.model.SpecQueueItemStatus;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueue;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueEvent;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueEventRepository;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueItemRepository;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/** Building blocks shared by user transitions and the runner. */
@Component
public class SpecQueueTransitions {

    private final SpecQueueRepository queues;
    private final SpecQueueItemRepository items;
    private final SpecQueueEventRepository events;
    private final TransactionTemplate newTransaction;
    private final Clock clock;

    public SpecQueueTransitions(SpecQueueRepository queues, SpecQueueItemRepository items,
                                SpecQueueEventRepository events, PlatformTransactionManager txManager,
                                ObjectProvider<Clock> clock) {
        this.queues = queues;
        this.items = items;
        this.events = events;
        this.newTransaction = new TransactionTemplate(txManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    /**
     * Creates the default queue row if missing, in its own transaction so a concurrent first call
     * can never abort the caller's transaction. A primary-key race is benign.
     */
    public void ensureQueueRow(UUID projectId) {
        if (queues.existsById(projectId)) {
            return;
        }
        try {
            newTransaction.executeWithoutResult(status ->
                    queues.saveAndFlush(new SpecQueue(projectId, clock.instant())));
        } catch (DataIntegrityViolationException e) {
            if (!queues.existsById(projectId)) {
                throw e;
            }
        }
    }

    /** Row lock on the queue row = per-project mutex. */
    @Transactional(propagation = Propagation.MANDATORY)
    public SpecQueue lockQueue(UUID projectId) {
        ensureQueueRow(projectId);
        return queues.findByProjectIdForUpdate(projectId)
                .orElseThrow(() -> new IllegalStateException("Spec queue row missing after creation"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void appendEvent(SpecQueueEvent event) {
        events.save(event);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<SpecQueueItem> compareAndTransition(UUID itemId, SpecQueueItemStatus expected,
                                                        SpecQueueItemStatus target) {
        return items.findById(itemId)
                .filter(item -> item.getStatus() == expected)
                .map(item -> {
                    item.transitionTo(target, clock.instant());
                    return items.saveAndFlush(item);
                });
    }
}
