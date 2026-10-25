package nl.metafactory.agents.workflow.agents;

import nl.metafactory.agents.catalog.AgentCatalogProperties;
import nl.metafactory.agents.catalog.LocalAgentCatalog;
import nl.metafactory.common.agentdefinition.ConfinedRoot;
import nl.metafactory.common.agentdefinition.InvalidDefinitionException;
import nl.metafactory.common.agentdefinition.SourceStatus;
import nl.metafactory.common.agentruntime.AgentExecutionRequest.DefinitionSnapshot;
import org.springframework.stereotype.Component;

import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Definition snapshots of local workflow agents: finds the agent by id anywhere below the repository
 * {@code /agents} folder (the catalog's Local section, subfolders included) and reads its file afresh. The
 * snapshot travels to the runtime with the execution request, so the runtime never needs this folder; it
 * parses and validates the Markdown itself.
 */
@Component
public class CatalogAgentDefinitions {

    private final LocalAgentCatalog catalog;
    private final AgentCatalogProperties properties;

    public CatalogAgentDefinitions(LocalAgentCatalog catalog, AgentCatalogProperties properties) {
        this.catalog = catalog;
        this.properties = properties;
    }

    public DefinitionSnapshot lookup(String agentId) {
        var section = catalog.list();
        if (!"OK".equals(section.status())) {
            return DefinitionSnapshot.of(SourceStatus.UNAVAILABLE, section.message());
        }
        var match = section.agents().stream().filter(a -> a.id().equals(agentId)).toList();
        if (match.isEmpty()) {
            return DefinitionSnapshot.of(SourceStatus.NOT_FOUND, "no local agent " + agentId);
        }
        if (match.size() > 1 || !match.getFirst().valid()) {
            return DefinitionSnapshot.of(SourceStatus.INVALID, "local agent " + agentId + " is invalid or duplicated");
        }
        try {
            var root = new ConfinedRoot(Path.of(properties.getLocalPath()));
            var file = root.resolve(match.getFirst().path().split("/")).orElse(null);
            if (file == null) {
                return DefinitionSnapshot.of(SourceStatus.NOT_FOUND, "local agent file disappeared");
            }
            return DefinitionSnapshot.found(ConfinedRoot.readBounded(file));
        } catch (InvalidDefinitionException e) {
            return DefinitionSnapshot.of(SourceStatus.INVALID, e.getMessage());
        } catch (UncheckedIOException e) {
            return DefinitionSnapshot.of(SourceStatus.UNAVAILABLE, "local agent file unreadable");
        }
    }
}
