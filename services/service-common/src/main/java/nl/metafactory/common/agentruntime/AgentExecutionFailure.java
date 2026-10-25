package nl.metafactory.common.agentruntime;

/**
 * Error body of the runtime's execution endpoints. {@code reason} is a stable code: a definition resolution
 * failure (e.g. {@code TENANT_FORBIDDEN}, {@code NOT_FOUND}, {@code UNAVAILABLE}) or one of the constants
 * below. {@code message} is safe to show: it never contains message content, prompts or secrets.
 */
public record AgentExecutionFailure(String executionId, String reason, String message) {

    /** The request is malformed (missing or unsafe fields). HTTP 400. */
    public static final String INVALID_REQUEST = "INVALID_REQUEST";

    /** The caller is not the agent service's identity. HTTP 403. */
    public static final String FORBIDDEN = "FORBIDDEN";

    /** An execution with this id already ran or is running; it is not run again. HTTP 409. */
    public static final String DUPLICATE_EXECUTION = "DUPLICATE_EXECUTION";

    /** A guardrail blocked the run. HTTP 422. */
    public static final String GUARDRAIL_VIOLATION = "GUARDRAIL_VIOLATION";

    /** The agent failed while running. HTTP 500. */
    public static final String EXECUTION_FAILED = "EXECUTION_FAILED";

    /** No execution with this id is known to the runtime. HTTP 404. */
    public static final String UNKNOWN_EXECUTION = "UNKNOWN_EXECUTION";
}
