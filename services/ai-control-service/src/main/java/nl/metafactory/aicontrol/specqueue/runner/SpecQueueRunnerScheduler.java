package nl.metafactory.aicontrol.specqueue.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Fixed delay (not rate) so ticks never overlap within one instance. */
@Component
@ConditionalOnProperty(prefix = "openjcockpit.spec-queue.runner", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SpecQueueRunnerScheduler {

    private static final Logger log = LoggerFactory.getLogger(SpecQueueRunnerScheduler.class);

    private final SpecQueueRunner runner;

    public SpecQueueRunnerScheduler(SpecQueueRunner runner) {
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${openjcockpit.spec-queue.runner.poll-interval:PT20S}")
    public void tick() {
        try {
            runner.tick();
        } catch (RuntimeException e) {
            log.warn("spec-queue.tick-failed error={}", e.getClass().getSimpleName());
        }
    }
}
