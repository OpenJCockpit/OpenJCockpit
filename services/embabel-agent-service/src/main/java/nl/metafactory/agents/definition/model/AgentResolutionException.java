package nl.metafactory.agents.definition.model;

import java.util.Objects;

/**
 * Thrown when no runnable {@link ResolvedAgentDefinition} can be produced. The message is safe to
 * log: it names ids and the failure kind, never instructions or credentials.
 */
public class AgentResolutionException extends RuntimeException {

    private final ResolutionFailure failure;

    public AgentResolutionException(ResolutionFailure failure, String message) {
        super(message);
        this.failure = Objects.requireNonNull(failure, "failure");
    }

    public ResolutionFailure failure() {
        return failure;
    }
}
