package nl.metafactory.agents.model;

/**
 * The identity that caused a workflow run to start, carried explicitly through
 * {@code AgentRunRequest} and {@code WorkflowExecutionService} rather than re-derived from the
 * thread-local security context at arbitrary points downstream. A {@link RunInitiatorKind#HUMAN}
 * initiator carries the real authenticated user's username and subject claim, taken from the
 * caller's JWT at the API boundary. A {@link RunInitiatorKind#TRIGGER} initiator represents an
 * automated caller (for example a Hermes signal, a project-folder file event, or a workflow orb
 * starting a child workflow); for a trigger, {@code username} doubles as a human-readable trigger
 * name and {@code subject} is always {@code null}.
 *
 * @param username for {@link RunInitiatorKind#HUMAN}, the caller's {@code preferred_username}
 *                  claim; for {@link RunInitiatorKind#TRIGGER}, the trigger's name
 * @param subject for {@link RunInitiatorKind#HUMAN}, the caller's {@code sub} claim; always
 *                 {@code null} for {@link RunInitiatorKind#TRIGGER}
 * @param kind whether this run was initiated by a human or an automated trigger
 */
public record RunInitiator(String username, String subject, RunInitiatorKind kind) {

    /**
     * The two categories of run initiator: a real authenticated human, or an automated trigger.
     */
    public enum RunInitiatorKind {
        HUMAN,
        TRIGGER
    }

    /**
     * Builds a {@link RunInitiatorKind#HUMAN} initiator from the caller's JWT claims.
     *
     * @param username the caller's {@code preferred_username} claim
     * @param subject the caller's {@code sub} claim
     * @return a human initiator carrying both claims
     */
    public static RunInitiator human(String username, String subject) {
        return new RunInitiator(username, subject, RunInitiatorKind.HUMAN);
    }

    /**
     * Builds a {@link RunInitiatorKind#TRIGGER} initiator representing an automated caller.
     *
     * @param triggerName a human-readable name for the trigger (for example
     *                     {@code "hermes-signal"} or {@code "workflow-orb"})
     * @return a trigger initiator with {@code subject} set to {@code null}
     */
    public static RunInitiator trigger(String triggerName) {
        return new RunInitiator(triggerName, null, RunInitiatorKind.TRIGGER);
    }
}
