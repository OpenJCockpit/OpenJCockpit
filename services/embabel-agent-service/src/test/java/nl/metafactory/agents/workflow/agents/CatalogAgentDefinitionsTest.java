package nl.metafactory.agents.workflow.agents;

import nl.metafactory.agents.catalog.AgentCatalog;
import nl.metafactory.agents.catalog.AgentCatalogProperties;
import nl.metafactory.agents.catalog.LocalAgentCatalog;
import nl.metafactory.agents.runtime.AgentRuntimeClient;
import nl.metafactory.agents.workflowrun.WorkflowRunScope;
import nl.metafactory.common.agentdefinition.SourceStatus;
import nl.metafactory.common.agentruntime.AgentExecutionRequest;
import nl.metafactory.common.agentruntime.AgentExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Local workflow agents are found anywhere below /agents, re-read from their file on every lookup, and sent
 * as a definition snapshot to the runtime, which runs them.
 */
class CatalogAgentDefinitionsTest {

    @TempDir
    Path root;

    private final LocalAgentCatalog catalog = mock(LocalAgentCatalog.class);
    private CatalogAgentDefinitions definitions;

    @BeforeEach
    void setUp() {
        var properties = new AgentCatalogProperties();
        properties.setLocalPath(root.toString());
        definitions = new CatalogAgentDefinitions(catalog, properties);
    }

    private static AgentCatalog.LocalAgent agent(String id, String path, boolean valid) {
        return new AgentCatalog.LocalAgent(id, id, null, "1", "m", path, List.of(), List.of(), List.of(), valid, null);
    }

    private void listed(AgentCatalog.LocalAgent... agents) {
        when(catalog.list()).thenReturn(new AgentCatalog.LocalSection("Local", "OK", null, List.of(agents)));
    }

    private String file(String path, String id) throws Exception {
        String markdown = "---\nid: " + id + "\nversion: \"1\"\nmodel: m\n---\n# Role\nx\n";
        Files.createDirectories(root.resolve(path).getParent());
        Files.writeString(root.resolve(path), markdown);
        return markdown;
    }

    @Test
    void findsAnAgentInASubfolderAndSnapshotsItsMarkdown() throws Exception {
        String markdown = file("delivery/qa-engineer.md", "qa-engineer");
        listed(agent("qa-engineer", "delivery/qa-engineer.md", true));

        var snapshot = definitions.lookup("qa-engineer");

        assertThat(snapshot.status()).isEqualTo(SourceStatus.FOUND);
        assertThat(snapshot.markdown()).isEqualTo(markdown);
    }

    @Test
    void anUnreadableFolderAnUnknownAgentAndAnInvalidOrDuplicatedAgentAreNotFound() {
        when(catalog.list()).thenReturn(new AgentCatalog.LocalSection("Local", "UNAVAILABLE", "not readable", List.of()));
        assertThat(definitions.lookup("qa-engineer").status()).isEqualTo(SourceStatus.UNAVAILABLE);

        listed(agent("other", "other.md", true));
        assertThat(definitions.lookup("qa-engineer").status()).isEqualTo(SourceStatus.NOT_FOUND);

        listed(agent("qa-engineer", "a.md", false));
        assertThat(definitions.lookup("qa-engineer").status()).isEqualTo(SourceStatus.INVALID);
        listed(agent("qa-engineer", "a.md", true), agent("qa-engineer", "b.md", true));
        assertThat(definitions.lookup("qa-engineer").status()).isEqualTo(SourceStatus.INVALID);
    }

    @Test
    void aFileThatDisappearedEscapesTheRootOrCannotBeReadIsRejected() throws Exception {
        listed(agent("qa-engineer", "gone.md", true));
        assertThat(definitions.lookup("qa-engineer").reason()).isEqualTo("local agent file disappeared");

        listed(agent("qa-engineer", "../outside.md", true));
        assertThat(definitions.lookup("qa-engineer").status()).isEqualTo(SourceStatus.INVALID);

        Files.createDirectories(root.resolve("dir.md"));
        listed(agent("qa-engineer", "dir.md", true));
        assertThat(definitions.lookup("qa-engineer").status()).isEqualTo(SourceStatus.INVALID);
    }

    @Test
    void anUnreadableFileIsUnavailable() throws Exception {
        file("locked.md", "qa-engineer");
        var target = root.resolve("locked.md");
        target.toFile().setReadable(false);
        try {
            listed(agent("qa-engineer", "locked.md", true));
            assertThat(definitions.lookup("qa-engineer").status()).isEqualTo(SourceStatus.UNAVAILABLE);
        } finally {
            target.toFile().setReadable(true);
        }
    }

    @Test
    void theRunnerSendsTheSnapshotAndOnlyTheRunIdToTheRuntime() throws Exception {
        file("qa-engineer.md", "qa-engineer");
        listed(agent("qa-engineer", "qa-engineer.md", true));
        var runtime = mock(AgentRuntimeClient.class);
        when(runtime.execute(any())).thenReturn(new AgentExecutionResult("e-1", "qa-engineer", "1", "h", "t", "done"));
        var runner = new LocalCatalogAgentRunner(definitions, runtime);
        var scope = new WorkflowRunScope("wf-1", "run-1", null);

        assertThat(runner.run("e-1", "local", "qa-engineer", "alice", "hi", scope)).isEqualTo("done");
        assertThat(runner.run("e-2", "local", "qa-engineer", "alice", "hi", null)).isEqualTo("done");

        var request = ArgumentCaptor.forClass(AgentExecutionRequest.class);
        verify(runtime, org.mockito.Mockito.times(2)).execute(request.capture());
        var first = request.getAllValues().getFirst();
        assertThat(first.executionId()).isEqualTo("e-1");
        assertThat(first.tenantId()).isEqualTo("local");
        assertThat(first.principal()).isEqualTo("alice");
        assertThat(first.workflowRunId()).isEqualTo("run-1");
        assertThat(first.message()).isEqualTo("hi");
        assertThat(first.definition().status()).isEqualTo(SourceStatus.FOUND);
        assertThat(request.getAllValues().get(1).workflowRunId()).isNull();
    }

    @Test
    void runtimeFailuresBecomeSafeInvocationErrors() {
        listed();
        var runtime = mock(AgentRuntimeClient.class);
        var runner = new LocalCatalogAgentRunner(definitions, runtime);

        when(runtime.execute(any())).thenThrow(new AgentRuntimeClient.AgentRuntimeException("TENANT_FORBIDDEN", "x", null));
        assertThatThrownBy(() -> runner.run("e-1", "local", "triage", "alice", "hi", null))
                .isInstanceOf(AgentInvocationException.class)
                .hasMessage("Local agent triage not runnable: TENANT_FORBIDDEN");

        org.mockito.Mockito.reset(runtime);
        when(runtime.execute(any())).thenThrow(new AgentRuntimeClient.AgentRuntimeException(
                AgentRuntimeClient.RUNTIME_UNAVAILABLE, "The agent runtime is unavailable (ResourceAccessException)", null));
        assertThatThrownBy(() -> runner.run("e-2", "local", "triage", "alice", "hi", null))
                .isInstanceOf(AgentInvocationException.class)
                .hasMessage("Local agent triage failed: The agent runtime is unavailable (ResourceAccessException)");
    }
}
