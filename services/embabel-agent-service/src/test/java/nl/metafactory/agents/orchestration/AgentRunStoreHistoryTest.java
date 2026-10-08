package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.persistence.InMemoryAgentRunPersistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunStoreHistoryTest {

    private AgentRunStore store;

    @BeforeEach
    void setUp() {
        store = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
    }

    @Test
    void listByWorkflowOnlyReturnsRunsForTheExactWorkflowId() {
        store.create("run-a", "cust1", "spec.md", "", "wf-1", "alice");
        store.create("run-b", "cust1", "spec.md", "", "wf-2", "alice");
        store.create("run-c", "cust1", "spec.md", "", null, "alice");

        var page = store.listByWorkflow("wf-1", 20, 0);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).runId()).isEqualTo("run-a");
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void listByWorkflowNeverMatchesANullWorkflowIdRun() {
        store.create("run-null", "cust1", "spec.md", "", null, "alice");

        var page = store.listByWorkflow("wf-1", 20, 0);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }

    @Test
    void listByWorkflowOrdersByStartedAtDescendingWithRunIdDescendingTieBreak() throws InterruptedException {
        store.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");
        Thread.sleep(5);
        store.create("run-2", "cust1", "spec.md", "", "wf-1", "alice");
        Thread.sleep(5);
        store.create("run-3", "cust1", "spec.md", "", "wf-1", "alice");

        var page = store.listByWorkflow("wf-1", 20, 0);

        assertThat(page.items()).extracting(r -> r.runId())
                .containsExactly("run-3", "run-2", "run-1");
    }

    @Test
    void listByWorkflowClampsLimitAndOffsetDefensively() {
        for (int i = 0; i < 5; i++) {
            store.create("run-" + i, "cust1", "spec.md", "", "wf-1", "alice");
        }

        var negativeOffset = store.listByWorkflow("wf-1", 20, -5);
        assertThat(negativeOffset.offset()).isZero();

        var zeroLimit = store.listByWorkflow("wf-1", 0, 0);
        assertThat(zeroLimit.limit()).isEqualTo(1);
        assertThat(zeroLimit.items()).hasSize(1);

        var overLimit = store.listByWorkflow("wf-1", 1000, 0);
        assertThat(overLimit.limit()).isEqualTo(100);
        assertThat(overLimit.items()).hasSize(5);
    }

    @Test
    void listByWorkflowSlicesBoundsSafelyWhenOffsetExceedsTotal() {
        store.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");

        var page = store.listByWorkflow("wf-1", 20, 50);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void listByWorkflowHasMoreReflectsRemainingItems() {
        for (int i = 0; i < 5; i++) {
            store.create("run-" + i, "cust1", "spec.md", "", "wf-1", "alice");
        }

        var firstPage = store.listByWorkflow("wf-1", 2, 0);
        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.hasMore()).isTrue();

        var lastPage = store.listByWorkflow("wf-1", 2, 4);
        assertThat(lastPage.items()).hasSize(1);
        assertThat(lastPage.hasMore()).isFalse();
    }

    @Test
    void completedAtIsStampedExactlyOnceOnFirstTerminalTransition() {
        store.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");

        store.setStatus("run-1", "COMPLETED");
        var firstCompletedAt = store.get("run-1").completedAt();
        assertThat(firstCompletedAt).isNotNull();

        store.setStatus("run-1", "FAILED");
        var secondCompletedAt = store.get("run-1").completedAt();

        assertThat(secondCompletedAt).isEqualTo(firstCompletedAt);
        assertThat(store.get("run-1").status()).isEqualTo("FAILED");
    }

    @Test
    void completedAtIsNeverStampedOnTransitionToAwaitingApproval() {
        store.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");

        store.setStatus("run-1", "AWAITING_APPROVAL");

        assertThat(store.get("run-1").completedAt()).isNull();
        assertThat(store.get("run-1").status()).isEqualTo("AWAITING_APPROVAL");
    }

    @Test
    void completedAtIsNeverStampedOnTransitionToRunning() {
        store.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");

        store.setStatus("run-1", "AWAITING_APPROVAL");
        store.setStatus("run-1", "RUNNING");

        assertThat(store.get("run-1").completedAt()).isNull();
        assertThat(store.get("run-1").status()).isEqualTo("RUNNING");
    }

    @Test
    void completedAtSurvivesAResumeFlowAndIsOnlySetOnTheFinalTerminalTransition() {
        store.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");

        store.setStatus("run-1", "AWAITING_APPROVAL");
        assertThat(store.get("run-1").completedAt()).isNull();

        store.setStatus("run-1", "RUNNING");
        assertThat(store.get("run-1").completedAt()).isNull();

        store.setStatus("run-1", "COMPLETED");
        var completedAt = store.get("run-1").completedAt();
        assertThat(completedAt).isNotNull();
    }
}
