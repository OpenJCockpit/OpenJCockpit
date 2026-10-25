package nl.metafactory.common.agentruntime;

import java.time.Instant;

/**
 * Lifecycle of one execution as the runtime knows it (in memory, bounded; lost on a runtime restart).
 * Used for correlation and diagnostics, e.g. to tell a caller whose request timed out whether the agent is
 * still running. Never carries the output.
 */
public record AgentExecutionStatus(String executionId, State state, Instant startedAt, Instant finishedAt,
                                   String reason) {

    public enum State { RUNNING, COMPLETED, FAILED }
}
