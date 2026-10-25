package nl.metafactory.agentruntime.definition.model;

/** Where the instructions of a resolved definition came from. */
public enum DefinitionSource {
    REMOTE_LITELLM,
    LAST_KNOWN_GOOD,
    LOCAL
}
