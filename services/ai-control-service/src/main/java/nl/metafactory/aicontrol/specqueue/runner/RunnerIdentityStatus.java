package nl.metafactory.aicontrol.specqueue.runner;

import nl.metafactory.aicontrol.config.SpecQueueProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/** Whether the runner may act: enabled by configuration, configured by a non-blank token secret. */
@Component
public class RunnerIdentityStatus {

    private static final Logger log = LoggerFactory.getLogger(RunnerIdentityStatus.class);
    private static final Duration WARNING_INTERVAL = Duration.ofMinutes(10);

    private final SpecQueueProperties.Runner config;
    private final Clock clock;
    private final AtomicReference<String> lastTickErrorCode = new AtomicReference<>();
    private final AtomicReference<Instant> lastWarning = new AtomicReference<>();

    public RunnerIdentityStatus(SpecQueueProperties properties, ObjectProvider<Clock> clock) {
        this.config = properties.getRunner();
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public boolean isConfigured() {
        return config.getToken().isConfigured();
    }

    public String lastTickErrorCode() {
        return lastTickErrorCode.get();
    }

    public void recordConfigured() {
        lastTickErrorCode.set(null);
    }

    /** The error code is set on every call; the warning at most once per 10 minutes. */
    public void recordUnconfigured() {
        lastTickErrorCode.set(RunnerPollErrorCodes.RUNNER_NOT_CONFIGURED);
        Instant now = clock.instant();
        Instant previous = lastWarning.get();
        if ((previous == null || !now.isBefore(previous.plus(WARNING_INTERVAL))) && lastWarning.compareAndSet(previous, now)) {
            log.warn("spec-queue.runner-not-configured: the runner token secret is blank, the runner stays idle");
        }
    }
}
