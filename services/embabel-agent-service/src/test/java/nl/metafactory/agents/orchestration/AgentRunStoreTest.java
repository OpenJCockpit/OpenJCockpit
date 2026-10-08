package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.persistence.InMemoryAgentRunPersistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunStoreTest {

    private AgentRunStore store;

    @BeforeEach
    void setUp() {
        store = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
    }

    @Test
    void createStoresARunningRunWithEmptyEventsAndArtifacts() {
        var run = store.create("run-1", "cust1", "spec.md", "https://github.com/org/repo", null, null);

        assertThat(run.runId()).isEqualTo("run-1");
        assertThat(run.customerId()).isEqualTo("cust1");
        assertThat(run.specFile()).isEqualTo("spec.md");
        assertThat(run.repositoryUrl()).isEqualTo("https://github.com/org/repo");
        assertThat(run.status()).isEqualTo("RUNNING");
        assertThat(run.events()).isEmpty();
        assertThat(run.generatedArtifacts()).isEmpty();
        assertThat(store.get("run-1")).isEqualTo(run);
    }

    @Test
    void getReturnsNullForAnUnknownRun() {
        assertThat(store.get("missing")).isNull();
    }

    @Test
    void recordEventAppendsToTheEventsListWithoutLosingEarlierEvents() {
        store.create("run-1", "cust1", "spec.md", "", null, null);

        store.recordEvent("run-1", "requirement", "Starting...", "RUNNING", "");
        store.recordEvent("run-1", "requirement", "Done", "OK", "evidence://requirements");

        var events = store.get("run-1").events();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).title()).isEqualTo("Starting...");
        assertThat(events.get(1).status()).isEqualTo("OK");
        assertThat(events.get(1).evidenceRef()).isEqualTo("evidence://requirements");
    }

    @Test
    void addArtifactAppendsToTheArtifactsListWithoutLosingEarlierArtifacts() {
        store.create("run-1", "cust1", "spec.md", "", null, null);

        store.addArtifact("run-1", "git-branch:feat/wf-1-run");
        store.addArtifact("run-1", "pull-request:https://github.com/org/repo/pull/9");

        assertThat(store.get("run-1").generatedArtifacts()).containsExactly(
                "git-branch:feat/wf-1-run", "pull-request:https://github.com/org/repo/pull/9");
    }

    @Test
    void setStatusChangesOnlyTheStatusField() {
        var created = store.create("run-1", "cust1", "spec.md", "https://github.com/org/repo", null, null);
        store.recordEvent("run-1", "requirement", "Done", "OK", "");

        store.setStatus("run-1", "COMPLETED");

        var updated = store.get("run-1");
        assertThat(updated.status()).isEqualTo("COMPLETED");
        assertThat(updated.runId()).isEqualTo(created.runId());
        assertThat(updated.customerId()).isEqualTo(created.customerId());
        assertThat(updated.startedAt()).isEqualTo(created.startedAt());
        assertThat(updated.events()).hasSize(1);
    }

    @Test
    void markCancelledThenIsCancelledReflectsTheFlag() {
        assertThat(store.isCancelled("run-1")).isFalse();

        store.markCancelled("run-1");

        assertThat(store.isCancelled("run-1")).isTrue();
        assertThat(store.isCancelled("run-2")).isFalse();
    }
}
