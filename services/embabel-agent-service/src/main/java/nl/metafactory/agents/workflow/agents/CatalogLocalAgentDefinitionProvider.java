package nl.metafactory.agents.workflow.agents;

import nl.metafactory.agents.catalog.AgentCatalogProperties;
import nl.metafactory.agents.catalog.LocalAgentCatalog;
import nl.metafactory.agents.definition.markdown.AgentMarkdownParser;
import nl.metafactory.agents.definition.markdown.InvalidDefinitionException;
import nl.metafactory.agents.definition.model.DefinitionSource;
import nl.metafactory.agents.definition.model.ProviderResult;
import nl.metafactory.agents.definition.model.SourceStatus;
import nl.metafactory.agents.definition.source.AgentDefinitionProvider;
import nl.metafactory.agents.definition.source.ConfinedRoot;

import java.nio.file.Path;

/**
 * Definition source for local workflow agents: finds the agent by id anywhere below the repository
 * {@code /agents} folder (the catalog's Local section, subfolders included).
 */
public class CatalogLocalAgentDefinitionProvider implements AgentDefinitionProvider {

    private final LocalAgentCatalog catalog;
    private final AgentCatalogProperties properties;
    private final AgentMarkdownParser parser;

    public CatalogLocalAgentDefinitionProvider(LocalAgentCatalog catalog, AgentCatalogProperties properties,
                                               AgentMarkdownParser parser) {
        this.catalog = catalog;
        this.properties = properties;
        this.parser = parser;
    }

    @Override
    public DefinitionSource source() {
        return DefinitionSource.LOCAL;
    }

    @Override
    public ProviderResult lookup(String agentId) {
        var section = catalog.list();
        if (!"OK".equals(section.status())) {
            return ProviderResult.of(SourceStatus.UNAVAILABLE, section.message());
        }
        var match = section.agents().stream().filter(a -> a.id().equals(agentId)).toList();
        if (match.isEmpty()) {
            return ProviderResult.of(SourceStatus.NOT_FOUND, "no local agent " + agentId);
        }
        if (match.size() > 1 || !match.getFirst().valid()) {
            return ProviderResult.of(SourceStatus.INVALID, "local agent " + agentId + " is invalid or duplicated");
        }
        try {
            var root = new ConfinedRoot(Path.of(properties.getLocalPath()));
            var segments = match.getFirst().path().split("/");
            var file = root.resolve(segments).orElse(null);
            if (file == null) {
                return ProviderResult.of(SourceStatus.NOT_FOUND, "local agent file disappeared");
            }
            var doc = parser.parse(ConfinedRoot.readBounded(file));
            return doc.id().equals(agentId) ? ProviderResult.found(doc)
                    : ProviderResult.of(SourceStatus.INVALID, "frontmatter id changed");
        } catch (InvalidDefinitionException e) {
            return ProviderResult.of(SourceStatus.INVALID, e.getMessage());
        }
    }
}
