package nl.metafactory.agents.workflowtrigger;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

/** Schedules a one-shot deadline task without holding a pipeline pool thread. */
public interface DeadlineScheduler {

    /** Schedules task to run once after delay, returning a cancellable handle. */
    ScheduledFuture<?> schedule(Runnable task, Duration delay);
}
