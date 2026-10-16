package nl.metafactory.agents.definition.model;

/**
 * Outcome of asking one definition source for one agent. Only {@link #NOT_FOUND} and
 * {@link #UNAVAILABLE} can ever be eligible for fallback; the rest are terminal.
 */
public enum SourceStatus {
    FOUND,
    NOT_FOUND,
    UNAVAILABLE,
    UNAUTHORIZED,
    INVALID,
    UNSUPPORTED;

    public boolean isFallbackCandidate() {
        return this == NOT_FOUND || this == UNAVAILABLE || this == UNSUPPORTED;
    }
}
