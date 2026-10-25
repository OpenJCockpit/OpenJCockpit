package nl.metafactory.agentruntime.definition.resolver;

import nl.metafactory.common.agentdefinition.AgentDefinitionDocument;
import nl.metafactory.agentruntime.definition.model.DefinitionSource;
import nl.metafactory.agentruntime.definition.model.SourceAttempt;

import java.util.List;

/** The definition document a source walk settled on, with the walk itself for explainability. */
public record DefinitionSelection(AgentDefinitionDocument document, DefinitionSource source, List<SourceAttempt> attempts) {

    public DefinitionSelection {
        attempts = List.copyOf(attempts);
    }
}
