package nl.metafactory.agents.runtime;

import nl.metafactory.agents.definition.binding.AgentTenantBindingRepository;
import nl.metafactory.agents.gitworkspace.GitWorkspaceBinding;
import nl.metafactory.agents.policy.PolicyDecisionService;
import nl.metafactory.agents.policy.model.PolicyDecision;
import nl.metafactory.agents.policy.model.PolicyDecisionContext;
import nl.metafactory.agents.workflowrun.WorkflowRunScope;
import nl.metafactory.agents.workflowrun.WorkflowRunScopes;
import nl.metafactory.common.agentruntime.AgentTenantBinding;
import nl.metafactory.common.agentruntime.ToolInvocationDecision;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The management data the runtime reads: only for the runtime's identity, and exactly what this service owns. */
class AgentRuntimeManagementControllerTest {

    private final AgentTenantBindingRepository bindings = mock(AgentTenantBindingRepository.class);
    private final WorkflowRunScopes runScopes = new WorkflowRunScopes();
    private final PolicyDecisionService policy = mock(PolicyDecisionService.class);
    private final AgentRuntimeManagementController management =
            new AgentRuntimeManagementController(bindings, runScopes, policy, new AgentRuntimeProperties());

    private static Jwt caller(String azp) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("s").claim("azp", azp)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    private static final Jwt RUNTIME = caller("openjcockpit-agent-runtime");

    @Test
    void onlyTheRuntimeIdentityGetsAnswers() {
        var user = caller("openjcockpit-dashboard");
        assertThat(management.tenantBinding("local", "qa", user).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(management.workflowRun("run-1", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(management.decide(new ToolInvocationDecision.Request(null, "r", "t", "a", "x", "p", null),
                caller("openjcockpit-agent-service")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(bindings, policy);
    }

    @Test
    void servesTenantBindingsAnd404sAMissingOne() {
        var binding = new AgentTenantBinding("local", "qa", true, "agent-chat", null, false, false,
                Set.of("git.read_file"), Set.of("pii-protection"));
        when(bindings.find("local", "qa")).thenReturn(Optional.of(binding));
        when(bindings.find("local", "nope")).thenReturn(Optional.empty());

        assertThat(management.tenantBinding("local", "qa", RUNTIME).getBody()).isEqualTo(binding);
        assertThat(management.tenantBinding("local", "nope", RUNTIME).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void servesTheScopeOfActiveRunsOnly() {
        runScopes.register(new WorkflowRunScope("wf-1", "run-1",
                new GitWorkspaceBinding("job-1", "https://git.example/r.git", "feature/x")));
        runScopes.register(new WorkflowRunScope("wf-2", "run-2", null));

        var run = management.workflowRun("run-1", RUNTIME).getBody();
        assertThat(run.workflowId()).isEqualTo("wf-1");
        assertThat(run.gitWorkspace().workspaceKey()).isEqualTo("job-1");
        assertThat(run.gitWorkspace().branch()).isEqualTo("feature/x");
        assertThat(management.workflowRun("run-2", RUNTIME).getBody().gitWorkspace()).isNull();

        runScopes.remove("run-1");
        assertThat(management.workflowRun("run-1", RUNTIME).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void toolDecisionsGoThroughThePolicyServiceWithTheSameContextAsBefore() {
        when(policy.canInvokeMcpTool(any())).thenReturn(new PolicyDecision(false, "workflowId is required", false,
                "medium", List.of(), null, null, null, Instant.now(), false, null));

        var response = management.decide(new ToolInvocationDecision.Request(null, "agent-run-1", "local", "qa",
                "git.read_file", "alice", Map.of("traceId", "t-1")), RUNTIME);

        assertThat(response.getBody()).isEqualTo(new ToolInvocationDecision(false, "workflowId is required"));
        var context = ArgumentCaptor.forClass(PolicyDecisionContext.class);
        verify(policy).canInvokeMcpTool(context.capture());
        assertThat(context.getValue().workflowId()).isNull();
        assertThat(context.getValue().workflowExecutionId()).isEqualTo("agent-run-1");
        assertThat(context.getValue().customerId()).isEqualTo("local");
        assertThat(context.getValue().triggerSource()).isEqualTo("a2a");
        assertThat(context.getValue().mcpToolName()).isEqualTo("git.read_file");
        assertThat(context.getValue().mcpToolOperation()).isEqualTo("invoke");
        assertThat(context.getValue().userId()).isEqualTo("alice");
        assertThat(context.getValue().additionalAttributes()).containsEntry("traceId", "t-1");
    }

    @Test
    void anIncompleteToolDecisionRequestIsRejected() {
        for (var bad : List.of(
                new ToolInvocationDecision.Request(null, "r", null, "a", "x", "p", null),
                new ToolInvocationDecision.Request(null, "r", "t", null, "x", "p", null),
                new ToolInvocationDecision.Request(null, "r", "t", "a", null, "p", null),
                new ToolInvocationDecision.Request(null, null, "t", "a", "x", "p", null))) {
            assertThat(management.decide(bad, RUNTIME).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        verifyNoInteractions(policy);
    }
}
