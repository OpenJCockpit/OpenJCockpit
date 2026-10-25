package nl.metafactory.common.agentdefinition;

/** A definition or skill file violates its contract. Message never contains file content. */
public class InvalidDefinitionException extends RuntimeException {

    public InvalidDefinitionException(String message) {
        super(message);
    }
}
