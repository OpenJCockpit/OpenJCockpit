package nl.metafactory.agents.definition.resolver;

import nl.metafactory.agents.definition.model.AgentDefinitionDocument;
import nl.metafactory.agents.definition.model.DefinitionSource;
import nl.metafactory.agents.definition.model.SourceAttempt;

import java.util.List;

/** The definition document a source walk settled on, with the walk itself for explainability. */
public record DefinitionSelection(AgentDefinitionDocument document, DefinitionSource source, List<SourceAttempt> attempts) {

    public DefinitionSelection {
        attempts = List.copyOf(attempts);
    }
}
