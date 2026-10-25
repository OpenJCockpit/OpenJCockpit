package nl.metafactory.agentruntime.definition.source;

import nl.metafactory.agentruntime.definition.model.DefinitionSource;
import nl.metafactory.common.agentdefinition.AgentMarkdownParser;
import nl.metafactory.common.agentdefinition.SourceStatus;
import nl.metafactory.common.agentruntime.AgentExecutionRequest.DefinitionSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A catalog snapshot is parsed and validated by the runtime like any local file. */
class SnapshotAgentDefinitionProviderTest {

    private static final String MARKDOWN = "---\nid: qa-engineer\nversion: \"1\"\nmodel: agent-chat\n---\n# Role\nTest it.\n";
    private final AgentMarkdownParser parser = new AgentMarkdownParser();

    @Test
    void aFoundSnapshotIsParsedAndMustNameTheRequestedAgent() {
        var provider = new SnapshotAgentDefinitionProvider(DefinitionSnapshot.found(MARKDOWN), parser);

        assertThat(provider.source()).isEqualTo(DefinitionSource.LOCAL);
        var found = provider.lookup("qa-engineer");
        assertThat(found.status()).isEqualTo(SourceStatus.FOUND);
        assertThat(found.document().version()).isEqualTo("1");

        assertThat(provider.lookup("software-developer").status()).isEqualTo(SourceStatus.INVALID);
    }

    @Test
    void anInvalidSnapshotOrTheCatalogsOwnFailureIsReportedUnchanged() {
        assertThat(new SnapshotAgentDefinitionProvider(DefinitionSnapshot.found("no frontmatter"), parser)
                .lookup("qa-engineer").status()).isEqualTo(SourceStatus.INVALID);

        var missing = new SnapshotAgentDefinitionProvider(
                DefinitionSnapshot.of(SourceStatus.NOT_FOUND, "no local agent qa-engineer"), parser).lookup("qa-engineer");
        assertThat(missing.status()).isEqualTo(SourceStatus.NOT_FOUND);
        assertThat(missing.reason()).isEqualTo("no local agent qa-engineer");
    }
}
