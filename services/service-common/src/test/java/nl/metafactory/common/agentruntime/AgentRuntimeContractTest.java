package nl.metafactory.common.agentruntime;

import nl.metafactory.common.agentdefinition.SourceStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The wire contract between agent service and runtime: shape, invariants and log-safety. */
class AgentRuntimeContractTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void executionRequestsRoundTripAndNeverPrintContent() {
        var request = new AgentExecutionRequest("run-1:e-1", "local", "qa-engineer", "alice", "run-1", null,
                "secret message", AgentExecutionRequest.DefinitionSnapshot.found("---\nsecret: markdown\n---\n"));

        var copy = json.readValue(json.writeValueAsString(request), AgentExecutionRequest.class);

        assertThat(copy).isEqualTo(request);
        assertThat(request.toString()).contains("run-1:e-1", "qa-engineer", "FOUND").doesNotContain("secret");
        assertThat(AgentExecutionRequest.EXECUTION_ID.matcher("run-1:7c9e6679-7425-40de-944b-e07fc1f90ae7").matches())
                .isTrue();
        assertThat(AgentExecutionRequest.EXECUTION_ID.matcher("a b").matches()).isFalse();
    }

    @Test
    void aSnapshotCarriesMarkdownExactlyWhenFound() {
        assertThat(AgentExecutionRequest.DefinitionSnapshot.of(SourceStatus.NOT_FOUND, "no local agent x").markdown())
                .isNull();
        assertThatThrownBy(() -> new AgentExecutionRequest.DefinitionSnapshot(SourceStatus.FOUND, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentExecutionRequest.DefinitionSnapshot(SourceStatus.INVALID, "md", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentExecutionRequest.DefinitionSnapshot(null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void resultsAndStatusesRoundTripWithoutPrintingTheAnswer() {
        var result = new AgentExecutionResult("e-1", "qa-engineer", "1.0.0", "hash", "trace", "secret answer");
        assertThat(json.readValue(json.writeValueAsString(result), AgentExecutionResult.class)).isEqualTo(result);
        assertThat(result.toString()).contains("e-1", "qa-engineer", "1.0.0", "trace").doesNotContain("secret");

        var status = new AgentExecutionStatus("e-1", AgentExecutionStatus.State.FAILED, Instant.EPOCH, Instant.EPOCH,
                AgentExecutionFailure.EXECUTION_FAILED);
        assertThat(json.readValue(json.writeValueAsString(status), AgentExecutionStatus.class)).isEqualTo(status);
        var failure = new AgentExecutionFailure("e-1", AgentExecutionFailure.DUPLICATE_EXECUTION, "not run again");
        assertThat(json.readValue(json.writeValueAsString(failure), AgentExecutionFailure.class)).isEqualTo(failure);
    }

    @Test
    void managementRecordsRoundTrip() {
        var run = new WorkflowRunDescriptor("wf-1", "run-1",
                new WorkflowRunDescriptor.GitWorkspace("job-1", "https://git.example/repo.git", "feature/x"));
        assertThat(json.readValue(json.writeValueAsString(run), WorkflowRunDescriptor.class)).isEqualTo(run);

        var binding = new AgentTenantBinding("local", "qa-engineer", true, "agent-chat", null, false, false,
                Set.of("git.read_file"), Set.of("pii-protection"));
        assertThat(json.readValue(json.writeValueAsString(binding), AgentTenantBinding.class)).isEqualTo(binding);
        var unrestricted = new AgentTenantBinding("t", "a", true, null, null, false, false, null, null);
        assertThat(unrestricted.allowedTools()).isEmpty();
        assertThat(unrestricted.requiredGuardrails()).isEmpty();
        assertThatThrownBy(() -> new AgentTenantBinding(null, "a", true, null, null, false, false, null, null))
                .isInstanceOf(NullPointerException.class);

        var request = new ToolInvocationDecision.Request(null, "run-1", "local", "qa", "git.read_file", "alice", null);
        assertThat(request.attributes()).isEmpty();
        var withAttributes = new ToolInvocationDecision.Request("wf", "run-1", "local", "qa", "git.read_file", "alice",
                Map.of("traceId", "t"));
        assertThat(json.readValue(json.writeValueAsString(withAttributes), ToolInvocationDecision.Request.class))
                .isEqualTo(withAttributes);
        assertThat(new ToolInvocationDecision(false, "denied").allowed()).isFalse();
    }

    @Test
    void draftKindsMapToPathSegmentsAndDraftsCarryTheDesignerShape() {
        assertThat(DefinitionDrafts.Kind.SUBAGENT.pathSegment()).isEqualTo("subagent");
        assertThat(DefinitionDrafts.Kind.fromPathSegment("skill")).contains(DefinitionDrafts.Kind.SKILL);
        assertThat(DefinitionDrafts.Kind.fromPathSegment("SKILL")).isEmpty();
        assertThat(DefinitionDrafts.Kind.fromPathSegment(null)).isEmpty();

        var agent = new DefinitionDrafts.AgentDraft("n", "d", "r", "i", List.of("s"), List.of("k"),
                List.of(new DefinitionDrafts.ToolRef("git", "git tools")), null, false);
        String wire = json.writeValueAsString(agent);
        assertThat(wire).contains("\"subagentNames\"", "\"skillNames\"", "\"mcpTools\"", "\"builtIn\"");
        assertThat(json.readValue(wire, DefinitionDrafts.AgentDraft.class)).isEqualTo(agent);
        assertThat(new DefinitionDrafts.SubagentDraft("n", "p", "d", "r", "i", List.of(), List.of(), null).parentAgent())
                .isEqualTo("p");
        assertThat(new DefinitionDrafts.SkillDraft("n", "d", "in", "out", "x", List.of(), "notes").policyNotes())
                .isEqualTo("notes");
        assertThat(new DefinitionDrafts.Request("concept").concept()).isEqualTo("concept");
        assertThat(AgentRuntimeApi.TENANT_BINDING).startsWith(AgentRuntimeApi.MANAGEMENT);
    }
}
