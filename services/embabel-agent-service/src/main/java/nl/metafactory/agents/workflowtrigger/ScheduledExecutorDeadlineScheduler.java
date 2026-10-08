package nl.metafactory.agents.workflowtrigger;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Production DeadlineScheduler backed by a single named-thread ScheduledExecutorService. */
@Component
public class ScheduledExecutorDeadlineScheduler implements DeadlineScheduler {

    private final ScheduledExecutorService executor;

    public ScheduledExecutorDeadlineScheduler() {
        this.executor = Executors.newSingleThreadScheduledExecutor(namedThreadFactory());
    }

    private static ThreadFactory namedThreadFactory() {
        AtomicInteger counter = new AtomicInteger(0);
        return runnable -> {
            Thread thread = new Thread(runnable, "workflow-trigger-deadline-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Duration delay) {
        return executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
    }
}
