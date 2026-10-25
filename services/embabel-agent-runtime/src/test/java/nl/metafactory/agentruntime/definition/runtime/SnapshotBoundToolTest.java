package nl.metafactory.agentruntime.definition.runtime;

import com.embabel.agent.api.tool.Tool;
import nl.metafactory.agentruntime.definition.tool.RegisteredAgentTool;
import nl.metafactory.agentruntime.definition.tool.RunScopedTool;
import nl.metafactory.agentruntime.gitworkspace.GitWorkspaceBinding;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotBoundToolTest {

    private static final RunContext RUN = new RunContext("run-1", "trace-1", "local", "software-developer", "1.1.0",
            "hash-1", "alice", new nl.metafactory.agentruntime.workflowrun.WorkflowRunScope("wf-1", "wf-run-1",
                    new GitWorkspaceBinding("job-1", "https://github.com/org/repo.git", "openjcockpit/wf/abc")));

    /** A run-scoped tool receives the bound run (and so its git workspace) on every call. */
    @Test
    void runScopedToolsGetTheRunTheyAreBoundTo() {
        var seen = new AtomicReference<RunContext>();
        var shape = Tool.create("git_read_file", "read", input -> Tool.Result.text("unused"));
        RunScopedTool scoped = new RunScopedTool() {
            @Override
            public Tool.Result call(String input, RunContext run) {
                seen.set(run);
                return Tool.Result.text("read");
            }

            @Override
            public Definition getDefinition() {
                return shape.getDefinition();
            }
        };
        var bound = new SnapshotBoundTool(new RegisteredAgentTool("git.read_file", scoped), Set.of("git.read_file"),
                (r, t) -> ToolCallAuthorizer.Decision.allow(), RUN);

        assertThat(bound.call("{}")).isInstanceOf(Tool.Result.Text.class);
        assertThat(seen.get()).isSameAs(RUN);
        assertThat(seen.get().gitWorkspace().workspaceKey()).isEqualTo("job-1");
    }

    @Test
    void theRunContextOmitsNoCorrelationButTheBindingIsOptional() {
        var unbound = new RunContext("run-1", "trace-1", "local", "a", "1", "h", "p");
        assertThat(unbound.gitWorkspace()).isNull();
        assertThat(RUN.logFields()).doesNotContain("github.com");
    }

    @Test
    void aToolOutsideTheSnapshotIsRefusedWithoutAskingThePolicy() {
        var tool = Tool.create("customer_read", "read", input -> Tool.Result.text("secret"));
        var asked = new java.util.concurrent.atomic.AtomicBoolean();
        var bound = new SnapshotBoundTool(new RegisteredAgentTool("customer.read", tool), Set.of("other.tool"),
                (r, t) -> {
                    asked.set(true);
                    return ToolCallAuthorizer.Decision.allow();
                }, RUN);

        assertThat(bound.call("{}")).isInstanceOf(Tool.Result.Error.class);
        assertThat(asked).isFalse();
        assertThat(bound.getMetadata()).isEqualTo(tool.getMetadata());
    }
}
