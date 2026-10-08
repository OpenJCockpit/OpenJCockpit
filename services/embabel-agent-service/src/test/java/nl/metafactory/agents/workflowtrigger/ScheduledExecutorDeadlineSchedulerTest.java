package nl.metafactory.agents.workflowtrigger;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledExecutorDeadlineSchedulerTest {

    @Test
    void scheduledTaskActuallyRunsAfterTheDelay() throws InterruptedException, ExecutionException, TimeoutException {
        var scheduler = new ScheduledExecutorDeadlineScheduler();
        var latch = new CountDownLatch(1);

        var future = scheduler.schedule(latch::countDown, Duration.ofMillis(50));

        // A generous wait margin (the scheduled delay itself is only 50ms) — this is a
        // real-wall-clock-timer test, and CI runners can occasionally be CPU-starved enough
        // to delay a freshly-created executor's first task well past a tight timeout. 15s
        // still proves the task genuinely runs; it does not change what the test verifies.
        boolean fired = latch.await(15, TimeUnit.SECONDS);

        assertThat(fired).isTrue();

        // latch.countDown() runs inside the task, but the ScheduledFuture only
        // transitions to done in a later step of the executor thread — waking from the
        // latch does not guarantee that transition has landed yet. future.get() blocks
        // until it has, so isDone() is race-free right after.
        future.get(15, TimeUnit.SECONDS);
        assertThat(future.isDone()).isTrue();
    }

    @Test
    void aCancelledTaskNeverRuns() throws InterruptedException {
        var scheduler = new ScheduledExecutorDeadlineScheduler();
        var latch = new CountDownLatch(1);

        var future = scheduler.schedule(latch::countDown, Duration.ofSeconds(5));
        boolean cancelled = future.cancel(false);

        boolean fired = latch.await(200, TimeUnit.MILLISECONDS);

        assertThat(cancelled).isTrue();
        assertThat(fired).isFalse();
    }
}
