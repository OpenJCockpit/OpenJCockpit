package nl.metafactory.agents.definition.source;

import nl.metafactory.agents.definition.markdown.AgentMarkdownParser;
import nl.metafactory.agents.definition.markdown.Identifiers;
import nl.metafactory.agents.definition.markdown.InvalidDefinitionException;
import nl.metafactory.agents.definition.model.DefinitionSource;
import nl.metafactory.agents.definition.model.ProviderResult;
import nl.metafactory.agents.definition.model.SourceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;

/** Reads exactly one {@code agents/<id>.md} below the configured, confined root. */
public class LocalMarkdownAgentDefinitionProvider implements AgentDefinitionProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalMarkdownAgentDefinitionProvider.class);

    private final ConfinedRoot root;
    private final AgentMarkdownParser parser;

    public LocalMarkdownAgentDefinitionProvider(ConfinedRoot root, AgentMarkdownParser parser) {
        this.root = root;
        this.parser = parser;
    }

    @Override
    public DefinitionSource source() {
        return DefinitionSource.LOCAL;
    }

    @Override
    public ProviderResult lookup(String agentId) {
        if (!Identifiers.isSlug(agentId)) {
            return ProviderResult.of(SourceStatus.INVALID, "agent id is not a valid identifier");
        }
        try {
            var file = root.resolve("agents", agentId + ".md");
            if (file.isEmpty()) {
                return ProviderResult.of(SourceStatus.NOT_FOUND, "no local definition");
            }
            var document = parser.parse(ConfinedRoot.readBounded(file.get()));
            if (!document.id().equals(agentId)) {
                return ProviderResult.of(SourceStatus.INVALID, "frontmatter id does not match file name");
            }
            return ProviderResult.found(document);
        } catch (InvalidDefinitionException e) {
            log.warn("agent.definition.invalid source=LOCAL agentId={} reason={}", agentId, e.getMessage());
            return ProviderResult.of(SourceStatus.INVALID, e.getMessage());
        } catch (UncheckedIOException e) {
            log.warn("agent.definition.unavailable source=LOCAL agentId={} error={}", agentId,
                    e.getCause().getClass().getSimpleName());
            return ProviderResult.of(SourceStatus.UNAVAILABLE, "local definition unreadable");
        }
    }
}
