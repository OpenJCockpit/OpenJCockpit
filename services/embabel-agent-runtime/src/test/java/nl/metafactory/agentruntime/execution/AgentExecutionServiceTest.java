package nl.metafactory.agentruntime.execution;

import com.embabel.agent.api.validation.guardrails.GuardRailViolationException;
import com.embabel.common.core.validation.ValidationSeverity;
import nl.metafactory.agentruntime.definition.model.AgentResolutionException;
import nl.metafactory.agentruntime.definition.model.ResolutionFailure;
import nl.metafactory.agentruntime.definition.runtime.AgentRunService;
import nl.metafactory.agentruntime.definition.runtime.CatalogAgentRuns;
import nl.metafactory.agentruntime.definition.runtime.ConfiguredDefinitionAgent;
import nl.metafactory.agentruntime.management.AgentServiceClient;
import nl.metafactory.agentruntime.workflowrun.WorkflowRunScope;
import nl.metafactory.agentruntime.workflowrun.WorkflowRunScopeResolver;
import nl.metafactory.common.agentruntime.AgentExecutionFailure;
import nl.metafactory.common.agentruntime.AgentExecutionRequest;
import nl.metafactory.common.agentruntime.AgentExecutionRequest.DefinitionSnapshot;
import nl.metafactory.common.agentruntime.AgentExecutionStatus.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentExecutionServiceTest {

    private static final ConfiguredDefinitionAgent.Response ANSWER =
            new ConfiguredDefinitionAgent.Response("qa-engineer", "1.1.0", "hash", "trace", "the answer");
    private static final WorkflowRunScope SCOPE = new WorkflowRunScope("wf-1", "run-1", null);

    private final AgentRunService served = mock(AgentRunService.class);
    private final AgentRunService catalogRun = mock(AgentRunService.class);
    private final CatalogAgentRuns catalog = mock(CatalogAgentRuns.class);
    private final WorkflowRunScopeResolver scopes = mock(WorkflowRunScopeResolver.class);
    private final ExecutionRegistry registry = new ExecutionRegistry();
    private final AgentExecutionService service = new AgentExecutionService(served, catalog, scopes, registry);

    @BeforeEach
    void setUp() {
        when(catalog.forSnapshot(any())).thenReturn(catalogRun);
        when(scopes.find("run-1")).thenReturn(Optional.of(SCOPE));
    }

    private static AgentExecutionRequest request(String id, String runId, DefinitionSnapshot definition) {
        return new AgentExecutionRequest(id, "local", "qa-engineer", "alice", runId, null, "msg", definition);
    }

    private AgentExecutionException failure(AgentExecutionRequest request) {
        try {
            service.execute(request);
        } catch (AgentExecutionException e) {
            return e;
        }
        throw new AssertionError("expected a failure");
    }

    @Test
    void aCatalogAgentRunsFromItsSnapshotInTheEnginesRunScope() {
        var snapshot = DefinitionSnapshot.found("---\nid: qa-engineer\n---\n");
        when(catalogRun.run("local", "qa-engineer", "alice", null, "msg", SCOPE)).thenReturn(ANSWER);

        var result = service.execute(request("e-1", "run-1", snapshot));

        assertThat(result.executionId()).isEqualTo("e-1");
        assertThat(result.text()).isEqualTo("the answer");
        assertThat(result.version()).isEqualTo("1.1.0");
        verify(catalog).forSnapshot(snapshot);
        verifyNoInteractions(served);
        assertThat(registry.find("e-1").orElseThrow().state()).isEqualTo(State.COMPLETED);
    }

    @Test
    void anAgentWithoutSnapshotRunsFromTheRuntimesOwnSourcesOutsideAnyWorkflow() {
        when(served.run(eq("local"), eq("qa-engineer"), eq("alice"), isNull(), eq("msg"), isNull())).thenReturn(ANSWER);

        assertThat(service.execute(request("e-2", null, null)).text()).isEqualTo("the answer");
        verifyNoInteractions(catalog, scopes);
    }

    @Test
    void aSecondRequestWithTheSameIdIsNeverRun() {
        when(served.run(any(), any(), any(), any(), any(), any())).thenReturn(ANSWER);
        service.execute(request("e-3", null, null));

        var duplicate = failure(request("e-3", null, null));

        assertThat(duplicate.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.reason()).isEqualTo(AgentExecutionFailure.DUPLICATE_EXECUTION);
        verify(served, times(1)).run(any(), any(), any(), any(), any(), any());
    }

    @Test
    void resolutionFailuresKeepTheirCodeAndOnlyUnavailabilityIsTransient() {
        when(served.run(any(), eq("qa-engineer"), any(), any(), any(), any()))
                .thenThrow(new AgentResolutionException(ResolutionFailure.TENANT_FORBIDDEN, "no binding"))
                .thenThrow(new AgentResolutionException(ResolutionFailure.UNAVAILABLE, "bindings unavailable"));

        var forbidden = failure(request("e-4", null, null));
        assertThat(forbidden.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(forbidden.reason()).isEqualTo("TENANT_FORBIDDEN");
        assertThat(forbidden.getMessage()).isEqualTo("Agent qa-engineer not runnable: TENANT_FORBIDDEN");
        assertThat(registry.find("e-4").orElseThrow().state()).isEqualTo(State.FAILED);

        var unavailable = failure(request("e-5", null, null));
        assertThat(unavailable.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(unavailable.reason()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void guardrailBlocksAndCrashesAreReportedWithoutContent() {
        when(served.run(any(), any(), any(), any(), any(), any()))
                .thenThrow(new GuardRailViolationException("pii", "contains a BSN", ValidationSeverity.CRITICAL))
                .thenThrow(new IllegalStateException("secret prompt text"));

        var blocked = failure(request("e-6", null, null));
        assertThat(blocked.reason()).isEqualTo(AgentExecutionFailure.GUARDRAIL_VIOLATION);
        assertThat(blocked.getMessage()).doesNotContain("BSN");

        var crashed = failure(request("e-7", null, null));
        assertThat(crashed.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(crashed.getMessage()).isEqualTo("Agent qa-engineer failed: IllegalStateException");
    }

    @Test
    void aNamedRunThatIsNotActiveOrCannotBeResolvedFailsTheExecution() {
        when(scopes.find("run-gone")).thenReturn(Optional.empty());
        when(scopes.find("run-down")).thenThrow(new AgentServiceClient.ManagementUnavailableException("down", null));

        var gone = failure(request("e-8", "run-gone", null));
        assertThat(gone.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(gone.getMessage()).contains("run-gone is not active");

        var down = failure(request("e-9", "run-down", null));
        assertThat(down.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verifyNoInteractions(served);
    }

    @Test
    void malformedRequestsAreRejectedBeforeAnythingRuns() {
        var bad = new AgentExecutionRequest[]{
                new AgentExecutionRequest(null, "t", "a", "p", null, null, "m", null),
                new AgentExecutionRequest("has space", "t", "a", "p", null, null, "m", null),
                new AgentExecutionRequest("e", " ", "a", "p", null, null, "m", null),
                new AgentExecutionRequest("e", "t", null, "p", null, null, "m", null),
                new AgentExecutionRequest("e", "t", "a", null, null, null, "m", null),
                new AgentExecutionRequest("e", "t", "a", "p", "run/../x", null, "m", null),
                new AgentExecutionRequest("e", "t", "a", "p", null, null, null, null),
                new AgentExecutionRequest("e", "t", "a", "p", null, null,
                        "x".repeat(AgentExecutionService.MAX_MESSAGE_CHARS + 1), null)};
        for (var request : bad) {
            assertThatThrownBy(() -> service.execute(request)).as(request.toString())
                    .isInstanceOfSatisfying(AgentExecutionException.class, e -> {
                        assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(e.reason()).isEqualTo(AgentExecutionFailure.INVALID_REQUEST);
                    });
        }
        verifyNoInteractions(served, catalog, scopes);
        assertThat(registry.find("e")).isEmpty();
    }
}
