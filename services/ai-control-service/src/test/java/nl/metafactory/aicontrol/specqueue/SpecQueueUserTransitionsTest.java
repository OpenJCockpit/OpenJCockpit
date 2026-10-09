package nl.metafactory.aicontrol.specqueue;

import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.SpecQueueEnqueueRequest;
import nl.metafactory.aicontrol.model.SpecQueueItemStatus;
import nl.metafactory.aicontrol.model.SpecQueueItemUpdateRequest;
import nl.metafactory.aicontrol.model.SpecQueueState;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import nl.metafactory.aicontrol.specqueue.app.CurrentActor;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueActiveItemGuard;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueException;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueException.Code;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueUserTransitions;
import nl.metafactory.aicontrol.specqueue.app.WorkflowBindingValidator.ValidatedWorkflow;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueEventRepository;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueItemRepository;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Drives the transaction beans directly against H2 to reach the refusal branches. */
@SpringBootTest
@TestPropertySource(properties = "openjcockpit.spec-queue.runner.max-queued-items=2")
class SpecQueueUserTransitionsTest {

    @Autowired SpecQueueUserTransitions transitions;
    @Autowired SpecQueueActiveItemGuard guard;
    @Autowired ProjectRepository projects;
    @Autowired SpecQueueRepository queues;
    @Autowired SpecQueueItemRepository items;
    @Autowired SpecQueueEventRepository events;
    @Autowired JdbcTemplate jdbc;

    private final CurrentActor actor = new CurrentActor("sub-1", "alice");
    private final ValidatedWorkflow workflow = new ValidatedWorkflow("wf", "WF");
    private UUID projectId;

    @BeforeEach
    void setUp() {
        for (String table : List.of("spec_queue_events", "spec_queue_item_pull_requests", "spec_queue_items", "spec_queues")) {
            jdbc.update("delete from " + table);
        }
        Project p = new Project();
        p.setName("p-" + UUID.randomUUID());
        projectId = projects.save(p).getId();
    }

    private SpecQueueItem add(String file) {
        return transitions.appendItem(projectId, new SpecQueueEnqueueRequest(file, "wf", false), workflow, 1, actor);
    }

    private SpecQueueItem withStatus(String file, SpecQueueItemStatus status) {
        SpecQueueItem item = items.findById(add(file).getId()).orElseThrow();
        item.transitionTo(status, Instant.now());
        return items.saveAndFlush(item);
    }

    private static Code codeOf(Runnable r) {
        try {
            r.run();
        } catch (SpecQueueException e) {
            return e.getCode();
        }
        return null;
    }

    @Test
    void appendEnforcesAutoMergePermissionAndCapacity() {
        assertThat(codeOf(() -> transitions.appendItem(projectId,
                new SpecQueueEnqueueRequest("x.md", "wf", true), workflow, 1, actor))).isEqualTo(Code.AUTO_MERGE_NOT_ALLOWED);
        add("a.md");
        add("b.md");
        assertThat(codeOf(() -> add("c.md"))).isEqualTo(Code.QUEUE_FULL);
    }

    @Test
    void updateRefusesNonQueuedItemsAndNewAutoMerge() {
        SpecQueueItem queued = add("a.md");
        assertThat(codeOf(() -> transitions.updateItem(projectId, queued.getId(),
                new SpecQueueItemUpdateRequest(null, true), null, actor))).isEqualTo(Code.AUTO_MERGE_NOT_ALLOWED);
        SpecQueueItem failed = withStatus("b.md", SpecQueueItemStatus.FAILED);
        assertThat(codeOf(() -> transitions.updateItem(projectId, failed.getId(),
                new SpecQueueItemUpdateRequest("wf", null), workflow, actor))).isEqualTo(Code.ITEM_NOT_EDITABLE);
        assertThat(codeOf(() -> transitions.updateItem(projectId, UUID.randomUUID(),
                new SpecQueueItemUpdateRequest("wf", null), workflow, actor))).isEqualTo(Code.ITEM_NOT_FOUND);
    }

    @Test
    void reorderOfAnEmptyQueueIsANoOp() {
        transitions.reorder(projectId, List.of(), actor);
        assertThat(events.findByProjectIdOrderByCreatedAtDesc(projectId, org.springframework.data.domain.PageRequest.of(0, 5))).isEmpty();
    }

    @Test
    void removalMatrixForActiveStatuses() {
        SpecQueueItem awaiting = withStatus("a.md", SpecQueueItemStatus.AWAITING_MERGE);
        assertThat(transitions.removeOrCancelIdleItem(projectId, awaiting.getId(), actor).getStatus())
                .isEqualTo(SpecQueueItemStatus.CANCELLED);
        assertThat(queues.findById(projectId).orElseThrow().getState()).isEqualTo(SpecQueueState.PAUSED);

        SpecQueueItem waitingForMerge = withStatus("b.md", SpecQueueItemStatus.MERGING);
        assertThat(transitions.removeOrCancelIdleItem(projectId, waitingForMerge.getId(), actor).getStatus())
                .isEqualTo(SpecQueueItemStatus.CANCELLED);

        SpecQueueItem merging = withStatus("c.md", SpecQueueItemStatus.MERGING);
        merging.recordMergeAttempt(Instant.now(), "sha");
        items.saveAndFlush(merging);
        assertThat(codeOf(() -> transitions.removeOrCancelIdleItem(projectId, merging.getId(), actor))).isEqualTo(Code.ITEM_BUSY);
        jdbc.update("delete from spec_queue_events");
        items.deleteAll();

        SpecQueueItem starting = withStatus("d.md", SpecQueueItemStatus.STARTING);
        assertThat(codeOf(() -> transitions.removeOrCancelIdleItem(projectId, starting.getId(), actor))).isEqualTo(Code.ITEM_BUSY);
        assertThat(codeOf(() -> guardCheck())).isEqualTo(Code.SPEC_QUEUE_ITEM_ACTIVE);
    }

    private void guardCheck() {
        guard.assertNoActiveItem(projectId);
    }

    @Test
    void retryAtTheFrontKeepsPositionsPositive() {
        SpecQueueItem first = add("a.md");
        SpecQueueItem failed = withStatus("b.md", SpecQueueItemStatus.FAILED);
        assertThat(first.getPosition()).isEqualTo(1);

        SpecQueueItem retried = transitions.retry(projectId, failed.getId(), actor);

        assertThat(retried.getStatus()).isEqualTo(SpecQueueItemStatus.QUEUED);
        assertThat(items.findByProjectIdOrderByPositionAsc(projectId))
                .extracting(SpecQueueItem::getId, SpecQueueItem::getPosition)
                .containsExactly(tuple(failed.getId(), 1L), tuple(first.getId(), 2L));
    }

    @Test
    void cancelBookkeeping() {
        SpecQueueItem queued = add("a.md");
        assertThat(codeOf(() -> transitions.beginCancel(projectId, queued.getId()))).isEqualTo(Code.ITEM_BUSY);
        SpecQueueItem running = withStatus("b.md", SpecQueueItemStatus.RUNNING);
        assertThat(codeOf(() -> transitions.beginCancel(projectId, running.getId()))).isEqualTo(Code.ITEM_BUSY);

        SpecQueueItem done = items.findById(queued.getId()).orElseThrow();
        done.recordCancelRequest(Instant.now());
        items.saveAndFlush(done);
        SpecQueueItem result = transitions.completeCancel(projectId, queued.getId(), actor);
        assertThat(result.getStatus()).isEqualTo(SpecQueueItemStatus.QUEUED);
        assertThat(result.getCancelRequestedAt()).isNull();

        transitions.abortCancel(projectId, UUID.randomUUID());
    }

    @Test
    void pauseAndResumeMatrix() {
        transitions.pause(projectId, actor);
        transitions.pause(projectId, actor);
        assertThat(events.findByProjectIdOrderByCreatedAtDesc(projectId, org.springframework.data.domain.PageRequest.of(0, 5))).hasSize(1);
        transitions.resume(projectId, actor);
        transitions.resume(projectId, actor);
        assertThat(queues.findById(projectId).orElseThrow().getState()).isEqualTo(SpecQueueState.ACTIVE);

        var queue = queues.findById(projectId).orElseThrow();
        queue.changeState(SpecQueueState.HALTED, Instant.now());
        queues.saveAndFlush(queue);
        assertThat(codeOf(() -> transitions.pause(projectId, actor))).isEqualTo(Code.QUEUE_HALTED);
    }

    @Test
    void skipRequiresFailedItemAndUnchangedSettingsWriteNothing() {
        SpecQueueItem queued = add("a.md");
        assertThat(codeOf(() -> transitions.skip(projectId, queued.getId(), actor))).isEqualTo(Code.ITEM_NOT_FAILED);

        transitions.changeAutoMergeAllowed(projectId, false, actor);
        assertThat(queues.findById(projectId).orElseThrow().getSettingsUpdatedAt()).isNull();
        transitions.changeAutoMergeAllowed(projectId, true, actor);
        assertThat(queues.findById(projectId).orElseThrow().getSettingsUpdatedAt()).isNotNull();
        guard.assertNoActiveItem(projectId);
        guard.assertNoActiveItem(null);
    }
}
