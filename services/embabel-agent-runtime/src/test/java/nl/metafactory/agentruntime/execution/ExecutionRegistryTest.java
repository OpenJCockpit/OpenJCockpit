package nl.metafactory.agentruntime.execution;

import nl.metafactory.common.agentruntime.AgentExecutionStatus.State;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** An execution id is accepted once — while running and for the retention period after it finished. */
class ExecutionRegistryTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-25T10:00:00Z"));
    private final ExecutionRegistry registry = new ExecutionRegistry(new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    });

    @Test
    void aRunningOrRecentlyFinishedIdIsNeverAcceptedAgain() {
        assertThat(registry.begin("e-1")).isTrue();
        assertThat(registry.begin("e-1")).as("still running").isFalse();
        assertThat(registry.find("e-1")).get().extracting(s -> s.state()).isEqualTo(State.RUNNING);

        registry.complete("e-1");
        assertThat(registry.begin("e-1")).as("finished, within retention").isFalse();
        var done = registry.find("e-1").orElseThrow();
        assertThat(done.state()).isEqualTo(State.COMPLETED);
        assertThat(done.finishedAt()).isEqualTo(now.get());

        assertThat(registry.begin("e-2")).isTrue();
        registry.fail("e-2", "EXECUTION_FAILED");
        assertThat(registry.find("e-2").orElseThrow().reason()).isEqualTo("EXECUTION_FAILED");
    }

    @Test
    void finishedEntriesExpireAfterTheRetentionButRunningOnesStay() {
        registry.begin("done");
        registry.complete("done");
        registry.begin("long-running");
        now.set(now.get().plus(ExecutionRegistry.RETENTION).plus(Duration.ofSeconds(1)));

        assertThat(registry.find("done")).isEmpty();
        assertThat(registry.find("long-running")).isPresent();
        assertThat(registry.find("unknown")).isEmpty();
    }

    @Test
    void finishingAnUnknownIdChangesNothing() {
        registry.complete("never-started");
        registry.fail("never-started", "x");
        assertThat(registry.find("never-started")).isEmpty();
    }

    @Test
    void theNumberOfFinishedEntriesIsBoundedOldestFirst() {
        for (int i = 0; i <= ExecutionRegistry.MAX_FINISHED; i++) {
            registry.begin("e-" + i);
            registry.complete("e-" + i);
        }
        registry.begin("running");

        assertThat(registry.find("e-0")).as("oldest finished entry evicted").isEmpty();
        assertThat(registry.find("e-1")).isPresent();
        assertThat(registry.find("running")).isPresent();
    }

    @Test
    void theDefaultRegistryUsesTheSystemClock() {
        var real = new ExecutionRegistry();
        assertThat(real.begin("x")).isTrue();
        assertThat(real.find("x").orElseThrow().startedAt()).isBeforeOrEqualTo(Instant.now());
    }
}
