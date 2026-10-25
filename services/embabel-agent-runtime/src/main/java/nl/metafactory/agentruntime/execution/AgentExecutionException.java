package nl.metafactory.agentruntime.execution;

import org.springframework.http.HttpStatus;

/** An execution request that did not produce an answer; the message is safe to return to the caller. */
public class AgentExecutionException extends RuntimeException {

    private final HttpStatus status;
    private final String reason;

    public AgentExecutionException(HttpStatus status, String reason, String message) {
        super(message);
        this.status = status;
        this.reason = reason;
    }

    public HttpStatus status() {
        return status;
    }

    public String reason() {
        return reason;
    }
}
