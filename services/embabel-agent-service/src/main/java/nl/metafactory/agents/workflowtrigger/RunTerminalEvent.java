package nl.metafactory.agents.workflowtrigger;

/** Published when a run transitions to a terminal status. */
public record RunTerminalEvent(String runId, String status) {
}
