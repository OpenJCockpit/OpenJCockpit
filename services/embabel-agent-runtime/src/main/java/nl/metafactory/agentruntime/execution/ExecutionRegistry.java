package nl.metafactory.agentruntime.execution;

import nl.metafactory.common.agentruntime.AgentExecutionStatus;
import nl.metafactory.common.agentruntime.AgentExecutionStatus.State;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The executions this runtime instance has accepted, by caller-chosen id. An id is accepted once: while it
 * runs and for {@link #RETENTION} after it finished a second request with that id is refused, so a retried
 * or duplicated request can never run an agent twice. In memory and bounded; finished entries are evicted
 * first by age, then oldest-first beyond {@link #MAX_FINISHED}. Never holds message or output content.
 */
public class ExecutionRegistry {

    static final Duration RETENTION = Duration.ofHours(1);
    static final int MAX_FINISHED = 10_000;

    private final Map<String, AgentExecutionStatus> executions = new LinkedHashMap<>();
    private final Clock clock;

    public ExecutionRegistry() {
        this(Clock.systemUTC());
    }

    ExecutionRegistry(Clock clock) {
        this.clock = clock;
    }

    /** Registers {@code executionId} as running; false when the id is already known (running or retained). */
    public synchronized boolean begin(String executionId) {
        evict();
        if (executions.containsKey(executionId)) {
            return false;
        }
        executions.put(executionId, new AgentExecutionStatus(executionId, State.RUNNING, clock.instant(), null, null));
        return true;
    }

    public synchronized void complete(String executionId) {
        finish(executionId, State.COMPLETED, null);
    }

    public synchronized void fail(String executionId, String reason) {
        finish(executionId, State.FAILED, reason);
    }

    public synchronized Optional<AgentExecutionStatus> find(String executionId) {
        evict();
        return Optional.ofNullable(executions.get(executionId));
    }

    private void finish(String executionId, State state, String reason) {
        var running = executions.remove(executionId);
        if (running != null) {
            // re-inserted, so insertion order is completion order for eviction
            executions.put(executionId, new AgentExecutionStatus(executionId, state, running.startedAt(),
                    clock.instant(), reason));
        }
    }

    private void evict() {
        var cutoff = clock.instant().minus(RETENTION);
        executions.values().removeIf(s -> s.finishedAt() != null && s.finishedAt().isBefore(cutoff));
        long finished = executions.values().stream().filter(s -> s.finishedAt() != null).count();
        var it = executions.values().iterator();
        while (finished > MAX_FINISHED && it.hasNext()) {
            if (it.next().finishedAt() != null) {
                it.remove();
                finished--;
            }
        }
    }
}
