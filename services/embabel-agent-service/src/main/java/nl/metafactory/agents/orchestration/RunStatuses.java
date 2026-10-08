package nl.metafactory.agents.orchestration;

/**
 * Holds the frozen run-status string literals used across the pipeline.
 *
 * <p>These constants are the single source of truth for the status values that
 * appear in run records, workflow definitions, and orchestration state machines.
 * Centralising them here prevents ad-hoc string literals from drifting apart
 * across services and makes it trivial to add or rename a status in one place.
 *
 * <p>The class also exposes {@link #isTerminal(String)} as the single source of
 * truth for which statuses are considered terminal (i.e. the run will not
 * transition to any further status).
 */
public final class RunStatuses {

    /** The run completed successfully. */
    public static final String COMPLETED = "COMPLETED";

    /** The run failed with an error. */
    public static final String FAILED = "FAILED";

    /** The run was explicitly cancelled by a user or system. */
    public static final String CANCELLED = "CANCELLED";

    /** The run was denied (e.g. by an approval gate). */
    public static final String DENIED = "DENIED";

    /** The run state was lost (e.g. due to a crash or restart). */
    public static final String RUN_STATE_LOST = "RUN_STATE_LOST";

    /** The run exceeded its allotted time budget. */
    public static final String TIMED_OUT = "TIMED_OUT";

    /** The run was blocked (e.g. refused by policy/scope when starting a workflow orb child). */
    public static final String BLOCKED = "BLOCKED";

    /** The run is waiting for a child workflow to complete. */
    public static final String AWAITING_CHILD_WORKFLOW = "AWAITING_CHILD_WORKFLOW";

    private RunStatuses() {
    }

    /**
     * Returns {@code true} if the given status is a terminal status, meaning the
     * run will not transition to any further status.
     *
     * <p>Terminal statuses are: {@link #COMPLETED}, {@link #FAILED},
     * {@link #CANCELLED}, {@link #DENIED}, {@link #RUN_STATE_LOST},
     * {@link #TIMED_OUT}, and {@link #BLOCKED}. Non-terminal statuses such as
     * {@link #AWAITING_CHILD_WORKFLOW} or any unknown value return {@code false}.
     *
     * @param status the status string to check; may be {@code null}
     * @return {@code true} if the status is terminal, {@code false} otherwise
     */
    public static boolean isTerminal(String status) {
        return COMPLETED.equals(status) || FAILED.equals(status) || CANCELLED.equals(status)
                || DENIED.equals(status) || RUN_STATE_LOST.equals(status) || TIMED_OUT.equals(status)
                || BLOCKED.equals(status);
    }
}