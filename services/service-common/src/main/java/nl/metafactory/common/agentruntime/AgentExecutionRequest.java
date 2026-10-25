package nl.metafactory.common.agentruntime;

import nl.metafactory.common.agentdefinition.SourceStatus;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One agent run requested by the workflow engine of {@code embabel-agent-service}.
 *
 * @param executionId   caller-chosen, unique id of this execution; the runtime refuses a second request with
 *                      the same id, so a retried or duplicated request can never run the agent twice
 * @param tenantId      tenant whose agent binding (tools, guardrails, pins) applies
 * @param agentId       the agent to run
 * @param principal     who the run is attributed to (policy and logs)
 * @param workflowRunId the workflow run this agent belongs to, or null; the runtime resolves its scope
 *                      (workflow, git workspace) from the agent service, never from this request
 * @param traceparent   optional W3C trace context
 * @param message       the message for the agent
 * @param definition    the agent definition as found in the agent service's catalog ({@code local:} agents),
 *                      or null to use the runtime's own definition sources (agents the runtime serves)
 */
public record AgentExecutionRequest(String executionId, String tenantId, String agentId, String principal,
                                    String workflowRunId, String traceparent, String message,
                                    DefinitionSnapshot definition) {

    /** Same rule as the runtime's run ids: safe to log and to use in a URL. */
    public static final Pattern EXECUTION_ID = Pattern.compile("[A-Za-z0-9._:-]{1,160}");

    /**
     * The catalog's lookup outcome for the agent: the raw Markdown when {@code status} is
     * {@link SourceStatus#FOUND}, otherwise why it is not available. The runtime parses and validates the
     * Markdown itself; a definition can only narrow what the tenant binding grants.
     */
    public record DefinitionSnapshot(SourceStatus status, String markdown, String reason) {

        public DefinitionSnapshot {
            Objects.requireNonNull(status, "status");
            if ((status == SourceStatus.FOUND) != (markdown != null)) {
                throw new IllegalArgumentException("markdown is present if and only if status is FOUND");
            }
        }

        public static DefinitionSnapshot found(String markdown) {
            return new DefinitionSnapshot(SourceStatus.FOUND, markdown, null);
        }

        public static DefinitionSnapshot of(SourceStatus status, String reason) {
            return new DefinitionSnapshot(status, null, reason);
        }

        @Override
        public String toString() {
            return "DefinitionSnapshot[status=" + status + ", reason=" + reason + "]";
        }
    }

    /** Message and definition content are never part of the string form (logs). */
    @Override
    public String toString() {
        return "AgentExecutionRequest[executionId=" + executionId + ", tenantId=" + tenantId + ", agentId=" + agentId
                + ", workflowRunId=" + workflowRunId + ", definition=" + definition + "]";
    }
}
