package nl.metafactory.aicontrol.specqueue;

import nl.metafactory.aicontrol.model.SpecQueueItemStatus;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueTransitions;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueue;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueEvent;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueEventType;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueEventRepository;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueItemRepository;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpecQueueTransitionsTest {

    private final UUID projectId = UUID.randomUUID();
    private SpecQueueRepository queues;
    private SpecQueueItemRepository items;
    private SpecQueueEventRepository events;
    private SpecQueueTransitions transitions;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        queues = mock(SpecQueueRepository.class);
        items = mock(SpecQueueItemRepository.class);
        events = mock(SpecQueueEventRepository.class);
        var txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ObjectProvider<Clock> clock = mock(ObjectProvider.class);
        when(clock.getIfAvailable(any())).thenReturn(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        transitions = new SpecQueueTransitions(queues, items, events, txManager, clock);
    }

    @Test
    void ensureQueueRowDoesNothingWhenRowExists() {
        when(queues.existsById(projectId)).thenReturn(true);
        transitions.ensureQueueRow(projectId);
        verify(queues, never()).saveAndFlush(any());
    }

    @Test
    void ensureQueueRowInsertsDefaultRow() {
        when(queues.existsById(projectId)).thenReturn(false);
        transitions.ensureQueueRow(projectId);
        verify(queues).saveAndFlush(any(SpecQueue.class));
    }

    @Test
    void primaryKeyRaceIsBenignButOtherViolationsPropagate() {
        when(queues.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("pk"));
        when(queues.existsById(projectId)).thenReturn(false, true);
        transitions.ensureQueueRow(projectId);

        when(queues.existsById(projectId)).thenReturn(false, false);
        assertThatThrownBy(() -> transitions.ensureQueueRow(projectId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lockQueueReturnsLockedRowOrFails() {
        when(queues.existsById(projectId)).thenReturn(true);
        var row = new SpecQueue(projectId, Instant.now());
        when(queues.findByProjectIdForUpdate(projectId)).thenReturn(Optional.of(row));
        assertThat(transitions.lockQueue(projectId)).isSameAs(row);

        when(queues.findByProjectIdForUpdate(projectId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> transitions.lockQueue(projectId)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void appendEventSavesTheEvent() {
        var event = new SpecQueueEvent(projectId, null, SpecQueueEventType.PAUSED, "USER:x", Instant.now());
        transitions.appendEvent(event);
        verify(events).save(event);
    }

    @Test
    void compareAndTransitionOnlyMovesMatchingItems() {
        var item = new SpecQueueItem(projectId, "a.md", "wf", null, false, 1, "s", null, Instant.now());
        when(items.findById(item.getId())).thenReturn(Optional.of(item));
        when(items.saveAndFlush(item)).thenReturn(item);

        assertThat(transitions.compareAndTransition(item.getId(), SpecQueueItemStatus.RUNNING, SpecQueueItemStatus.FAILED)).isEmpty();
        assertThat(item.getStatus()).isEqualTo(SpecQueueItemStatus.QUEUED);
        var moved = transitions.compareAndTransition(item.getId(), SpecQueueItemStatus.QUEUED, SpecQueueItemStatus.STARTING);
        assertThat(moved).containsSame(item);
        assertThat(item.getStatus()).isEqualTo(SpecQueueItemStatus.STARTING);
        assertThat(transitions.compareAndTransition(UUID.randomUUID(), SpecQueueItemStatus.QUEUED, SpecQueueItemStatus.STARTING)).isEmpty();
    }
}
