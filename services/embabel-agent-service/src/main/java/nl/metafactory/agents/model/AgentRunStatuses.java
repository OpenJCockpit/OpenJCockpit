package nl.metafactory.agents.model;

import java.util.Set;

/** The terminal-status set for an {@link AgentRun}. AWAITING_APPROVAL and RUNNING are explicitly
 * not terminal — a paused, awaiting-approval run must not be treated as finished. BLOCKED and
 * TIMED_OUT (workflow-trigger-workflow-orb: a parent refused/timed out waiting on a child) are
 * included because {@code AgentRunStore.setStatus} genuinely receives them for a run that will
 * never progress further — omitting them would leave such a run's {@code completedAt} unset
 * forever and make the startup reconciliation package treat it as crashed/interrupted. RUN_STATE_LOST
 * is a fabricated, synthetic status that is never actually stored and never reaches
 * {@code AgentRunStore.setStatus} in this package, so it is intentionally absent from
 * {@link #TERMINAL_STATUSES}; it is written only by the reconciliation package (a later work
 * package) when a run is found interrupted at startup, via the persistence layer directly. */
public final class AgentRunStatuses {

    public static final Set<String> TERMINAL_STATUSES =
            Set.of("COMPLETED", "FAILED", "CANCELLED", "DENIED", "BLOCKED", "TIMED_OUT");

    /** Sentinel status for a run found non-terminal (interrupted) at startup by a later
     * reconciliation package. Deliberately not a member of {@link #TERMINAL_STATUSES}. */
    public static final String RUN_STATE_LOST = "RUN_STATE_LOST";

    private AgentRunStatuses() {
    }

    public static boolean isTerminal(String status) {
        return status != null && TERMINAL_STATUSES.contains(status);
    }

    /** True for any non-null, non-terminal status (e.g. RUNNING, AWAITING_APPROVAL) — used by a
     * later reconciliation package to find runs left open at shutdown. */
    public static boolean isOpen(String status) {
        return status != null && !TERMINAL_STATUSES.contains(status);
    }
}
