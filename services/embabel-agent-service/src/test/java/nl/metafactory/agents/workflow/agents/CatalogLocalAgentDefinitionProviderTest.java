package nl.metafactory.agents.workflow.agents;

import nl.metafactory.agents.catalog.AgentCatalog;
import nl.metafactory.agents.catalog.AgentCatalogProperties;
import nl.metafactory.agents.catalog.LocalAgentCatalog;
import nl.metafactory.agents.definition.markdown.AgentMarkdownParser;
import nl.metafactory.agents.definition.model.DefinitionSource;
import nl.metafactory.agents.definition.model.SourceStatus;
import nl.metafactory.agents.definition.runtime.AgentRunService;
import nl.metafactory.agents.definition.runtime.ConfiguredDefinitionAgent;
import nl.metafactory.agents.workflowrun.WorkflowRunScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Local workflow agents are found anywhere below /agents and re-read from their file on every lookup. */
class CatalogLocalAgentDefinitionProviderTest {

    @TempDir
    Path root;

    private final LocalAgentCatalog catalog = mock(LocalAgentCatalog.class);
    private CatalogLocalAgentDefinitionProvider provider;

    @BeforeEach
    void setUp() {
        var properties = new AgentCatalogProperties();
        properties.setLocalPath(root.toString());
        provider = new CatalogLocalAgentDefinitionProvider(catalog, properties, new AgentMarkdownParser());
    }

    private static AgentCatalog.LocalAgent agent(String id, String path, boolean valid) {
        return new AgentCatalog.LocalAgent(id, id, null, "1", "m", path, List.of(), List.of(), List.of(), valid, null);
    }

    private void listed(AgentCatalog.LocalAgent... agents) {
        when(catalog.list()).thenReturn(new AgentCatalog.LocalSection("Local", "OK", null, List.of(agents)));
    }

    private void file(String path, String id) throws Exception {
        Files.createDirectories(root.resolve(path).getParent());
        Files.writeString(root.resolve(path), "---\nid: " + id + "\nversion: \"1\"\nmodel: m\n---\n# Rol\nx\n");
    }

    @Test
    void findsAnAgentInASubfolder() throws Exception {
        file("delivery/qa-engineer.md", "qa-engineer");
        listed(agent("qa-engineer", "delivery/qa-engineer.md", true));

        var result = provider.lookup("qa-engineer");

        assertThat(result.status()).isEqualTo(SourceStatus.FOUND);
        assertThat(result.document().id()).isEqualTo("qa-engineer");
        assertThat(provider.source()).isEqualTo(DefinitionSource.LOCAL);
    }

    @Test
    void anUnreadableFolderAnUnknownAgentAndAnInvalidOrDuplicatedAgentAreNotFound() {
        when(catalog.list()).thenReturn(new AgentCatalog.LocalSection("Local", "UNAVAILABLE", "not readable", List.of()));
        assertThat(provider.lookup("qa-engineer").status()).isEqualTo(SourceStatus.UNAVAILABLE);

        listed(agent("other", "other.md", true));
        assertThat(provider.lookup("qa-engineer").status()).isEqualTo(SourceStatus.NOT_FOUND);

        listed(agent("qa-engineer", "a.md", false));
        assertThat(provider.lookup("qa-engineer").status()).isEqualTo(SourceStatus.INVALID);
        listed(agent("qa-engineer", "a.md", true), agent("qa-engineer", "b.md", true));
        assertThat(provider.lookup("qa-engineer").status()).isEqualTo(SourceStatus.INVALID);
    }

    @Test
    void aFileThatDisappearedChangedItsIdOrNoLongerParsesIsRejected() throws Exception {
        listed(agent("qa-engineer", "gone.md", true));
        assertThat(provider.lookup("qa-engineer").reason()).isEqualTo("local agent file disappeared");

        file("renamed.md", "other-id");
        listed(agent("qa-engineer", "renamed.md", true));
        assertThat(provider.lookup("qa-engineer").reason()).isEqualTo("frontmatter id changed");

        Files.writeString(root.resolve("broken.md"), "no frontmatter");
        listed(agent("qa-engineer", "broken.md", true));
        assertThat(provider.lookup("qa-engineer").status()).isEqualTo(SourceStatus.INVALID);
    }

    @Test
    void theRunnerRunsTheAgentWithOrWithoutAWorkflowScope() {
        var runs = mock(AgentRunService.class);
        var runner = new LocalCatalogAgentRunner(runs);
        var scope = new WorkflowRunScope("wf-1", "run-1", null);
        var response = new ConfiguredDefinitionAgent.Response("qa-engineer", "1", "h", "t", "done");
        when(runs.run("local", "qa-engineer", "alice", null, "hi", null)).thenReturn(response);
        when(runs.run("local", "qa-engineer", "alice", null, "hi", scope)).thenReturn(response);

        assertThat(runner.run("local", "qa-engineer", "alice", "hi")).isSameAs(response);
        assertThat(runner.run("local", "qa-engineer", "alice", "hi", scope)).isSameAs(response);
        verify(runs).run("local", "qa-engineer", "alice", null, "hi", scope);
    }
}
