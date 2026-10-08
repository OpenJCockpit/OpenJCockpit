package nl.metafactory.aicontrol.specqueue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItemPullRequest;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueItemPullRequestRepository;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SpecQueuePersistenceTest {

    @Autowired ProjectRepository projects;
    @Autowired SpecQueueItemRepository items;
    @Autowired SpecQueueItemPullRequestRepository pullRequests;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @PersistenceContext EntityManager em;

    UUID projectId;

    @BeforeEach
    void setUp() {
        for (String table : List.of("spec_queue_events", "spec_queue_item_pull_requests", "spec_queue_items", "spec_queues")) {
            jdbc.update("delete from " + table);
        }
        Project p = new Project();
        p.setName("p-" + UUID.randomUUID());
        projectId = projects.save(p).getId();
    }

    @Test
    void pullRequestsRoundTripOrderedByCreation() {
        SpecQueueItem item = items.saveAndFlush(new SpecQueueItem(projectId, "a.md", "wf", null, false, 1, "s", null, Instant.now()));
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        var second = new SpecQueueItemPullRequest(item.getId(), "run-1", "https://github.com/o/r/pull/2", "o", "r", 2, t.plusSeconds(5));
        var first = new SpecQueueItemPullRequest(item.getId(), "run-1", "https://github.com/o/r/pull/1", "o", "r", 1, t);
        first.markMerged(t.plusSeconds(9));
        pullRequests.saveAllAndFlush(List.of(second, first));
        em.clear();

        var loaded = pullRequests.findByItemIdOrderByCreatedAtAsc(item.getId());

        assertThat(loaded).extracting(SpecQueueItemPullRequest::getNumber).containsExactly(1, 2);
        assertThat(loaded.get(0).getMergedAt()).isEqualTo(t.plusSeconds(9));
        assertThat(loaded.get(1).getMergedAt()).isNull();
    }

    @Test
    void itemRoundTripStartsAtVersionZeroAndKeepsDerivedColumns() {
        SpecQueueItem saved = items.saveAndFlush(new SpecQueueItem(projectId, "a.md", "wf", null, false, 1, "s", null, Instant.now()));
        em.clear();
        SpecQueueItem loaded = items.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getVersion()).isZero();
        assertThat(loaded.getOpenSpecKey()).isEqualTo("a.md");
        assertThat(loaded.getActiveSlot()).isNull();
        assertThat(items.findProjectIdsByStatusIn(List.of(loaded.getStatus()))).contains(projectId);
        assertThat(items.findProjectIdsByStatusIn(List.of(nl.metafactory.aicontrol.model.SpecQueueItemStatus.RUNNING)))
                .doesNotContain(projectId);
    }
}
