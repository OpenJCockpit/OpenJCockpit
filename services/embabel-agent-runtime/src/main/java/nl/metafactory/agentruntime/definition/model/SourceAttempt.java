package nl.metafactory.agentruntime.definition.model;

import nl.metafactory.common.agentdefinition.SourceStatus;
import java.util.Objects;

/** One step of the resolver's source walk, kept for explainability. Never contains content. */
public record SourceAttempt(DefinitionSource source, SourceStatus status, String reason) {

    public SourceAttempt {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(status, "status");
    }
}
