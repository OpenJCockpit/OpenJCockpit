package nl.metafactory.agents.workflow.agents;

/** A referenced agent could not be run; the message is safe to show in run events (no content, no secrets). */
public class AgentInvocationException extends RuntimeException {

    public AgentInvocationException(String message) {
        super(message);
    }
}
