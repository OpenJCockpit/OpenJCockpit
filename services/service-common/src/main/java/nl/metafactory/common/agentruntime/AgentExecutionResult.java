package nl.metafactory.common.agentruntime;

/**
 * Successful outcome of an {@link AgentExecutionRequest}: the agent's text answer plus the identity of the
 * definition snapshot that produced it.
 */
public record AgentExecutionResult(String executionId, String agentId, String version, String contentHash,
                                   String traceId, String text) {

    /** The answer text is never part of the string form (logs). */
    @Override
    public String toString() {
        return "AgentExecutionResult[executionId=" + executionId + ", agentId=" + agentId + ", version=" + version
                + ", traceId=" + traceId + "]";
    }
}
