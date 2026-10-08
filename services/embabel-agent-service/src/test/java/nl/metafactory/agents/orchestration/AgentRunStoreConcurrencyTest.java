package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.persistence.InMemoryAgentRunPersistence;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

class AgentRunStoreConcurrencyTest {

    @Test
    void recordEventOnAnUnknownRunIdIsASafeNoOp() {
        var store = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });

        assertThatCode(() -> store.recordEvent("unknown-run", "agent", "title", "OK", "evidence://x"))
                .doesNotThrowAnyException();
        assertThat(store.get("unknown-run")).isNull();
    }

    @Test
    void addArtifactOnAnUnknownRunIdIsASafeNoOp() {
        var store = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });

        assertThatCode(() -> store.addArtifact("unknown-run", "git-branch:feat/x"))
                .doesNotThrowAnyException();
        assertThat(store.get("unknown-run")).isNull();
    }

    @Test
    void setStatusOnAnUnknownRunIdIsASafeNoOp() {
        var store = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });

        assertThatCode(() -> store.setStatus("unknown-run", "COMPLETED"))
                .doesNotThrowAnyException();
        assertThat(store.get("unknown-run")).isNull();
    }

    @Test
    void concurrentRecordEventCallsOnTheSameRunNeverLoseAnUpdate() throws InterruptedException {
        var store = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
        store.create("run-1", "cust1", "spec.md", "", "wf-1", "alice");

        int threadCount = 20;
        var latch = new CountDownLatch(threadCount);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            for (int i = 0; i < threadCount; i++) {
                int index = i;
                pool.submit(() -> {
                    try {
                        store.recordEvent("run-1", "agent-" + index, "title-" + index, "OK", "evidence://" + index);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdown();
        }

        assertThat(store.get("run-1").events()).hasSize(threadCount);
    }

    @Test
    void concurrentSetStatusAndRecordEventCallsOnTheSameRunDoNotCorruptState() throws InterruptedException {
        var store = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
        store.create("run-2", "cust1", "spec.md", "", "wf-1", "alice");

        int eventThreads = 10;
        var latch = new CountDownLatch(eventThreads + 1);
        ExecutorService pool = Executors.newFixedThreadPool(eventThreads + 1);
        try {
            for (int i = 0; i < eventThreads; i++) {
                int index = i;
                pool.submit(() -> {
                    try {
                        store.recordEvent("run-2", "agent-" + index, "title-" + index, "OK", "");
                    } finally {
                        latch.countDown();
                    }
                });
            }
            pool.submit(() -> {
                try {
                    store.setStatus("run-2", "COMPLETED");
                } finally {
                    latch.countDown();
                }
            });
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdown();
        }

        var run = store.get("run-2");
        assertThat(run.events()).hasSize(eventThreads);
        assertThat(run.status()).isEqualTo("COMPLETED");
        assertThat(run.completedAt()).isNotNull();
    }
}
