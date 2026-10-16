package nl.metafactory.agents.definition.markdown;

/** A definition or skill file violates its contract. Message never contains file content. */
public class InvalidDefinitionException extends RuntimeException {

    public InvalidDefinitionException(String message) {
        super(message);
    }

    public InvalidDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
