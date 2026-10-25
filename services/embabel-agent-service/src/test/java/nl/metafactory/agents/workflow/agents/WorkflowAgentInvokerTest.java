package nl.metafactory.agents.workflow.agents;

import nl.metafactory.agents.a2a.client.A2AProviderClient;
import nl.metafactory.agents.a2a.client.A2AProviderClient.Status;
import nl.metafactory.agents.a2a.client.A2AProviderClients;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowAgentInvokerTest {

    final LocalCatalogAgentRunner runner = mock(LocalCatalogAgentRunner.class);
    final A2AProviderClients providers = mock(A2AProviderClients.class);
    final A2AProviderClient litellm = mock(A2AProviderClient.class);
    final AgentWorkflowProperties props = new AgentWorkflowProperties();
    final WorkflowAgentInvoker.Invocation context = new WorkflowAgentInvoker.Invocation("run-1", "acme", "alice");

    WorkflowAgentInvoker invoker(LocalCatalogAgentRunner localRunner) {
        return new WorkflowAgentInvoker(localRunner, providers, props);
    }

    @Test
    void localAgentsRunOnTheRuntimeForTheConfiguredTenantOrTheCustomer() {
        when(runner.run(any(), any(), eq("triage"), eq("alice"), eq("msg"), isNull())).thenReturn("local answer");
        assertThat(invoker(runner).invoke(AgentRef.local("triage"), "msg", context)).isEqualTo("local answer");
        verify(runner).run(org.mockito.ArgumentMatchers.startsWith("run-1:"), eq("acme"), eq("triage"), eq("alice"),
                eq("msg"), isNull());

        props.setTenant("local");
        invoker(runner).invoke(AgentRef.local("triage"), "msg", context);
        verify(runner).run(any(), eq("local"), eq("triage"), eq("alice"), eq("msg"), isNull());
    }

    @Test
    void everyLocalInvocationGetsItsOwnExecutionIdAndAWorkflowPrincipalWhenNoneIsKnown() {
        var ids = org.mockito.ArgumentCaptor.forClass(String.class);
        var anonymous = new WorkflowAgentInvoker.Invocation(null, "acme", null);
        invoker(runner).invoke(AgentRef.local("triage"), "msg", anonymous);
        invoker(runner).invoke(AgentRef.local("triage"), "msg", anonymous);
        verify(runner, org.mockito.Mockito.times(2)).run(ids.capture(), eq("acme"), eq("triage"), eq("workflow"),
                eq("msg"), isNull());
        assertThat(ids.getAllValues().get(0)).doesNotContain(":").isNotEqualTo(ids.getAllValues().get(1));
    }

    @Test
    void localInvocationFailuresPropagateAsSafeInvocationErrors() {
        when(runner.run(any(), any(), any(), any(), any(), any()))
                .thenThrow(new AgentInvocationException("Local agent triage not runnable: TENANT_FORBIDDEN"));
        assertThatThrownBy(() -> invoker(runner).invoke(AgentRef.local("triage"), "msg", context))
                .isInstanceOf(AgentInvocationException.class).hasMessageContaining("TENANT_FORBIDDEN");
    }

    @Test
    void a2aAgentsRunThroughTheProvidersSingleClient() {
        when(providers.find("litellm")).thenReturn(Optional.of(litellm));
        when(litellm.name()).thenReturn("LiteLLM");
        when(litellm.sendMessage(eq("qa-engineer"), eq("msg"), isNull(), eq(java.util.Map.of())))
                .thenReturn(new A2AProviderClient.MessageResult(Status.OK, "verified", null));
        assertThat(invoker(runner).invoke(AgentRef.a2a("litellm", "qa-engineer"), "msg", context)).isEqualTo("verified");

        when(litellm.sendMessage(eq("software-developer"), any(), any(), any()))
                .thenReturn(new A2AProviderClient.MessageResult(Status.INVALID, null, "-32600 Request not permitted (TENANT_FORBIDDEN)"));
        assertThatThrownBy(() -> invoker(runner).invoke(AgentRef.a2a("litellm", "software-developer"), "msg", context))
                .isInstanceOf(AgentInvocationException.class).hasMessageContaining("TENANT_FORBIDDEN");
        assertThatThrownBy(() -> invoker(runner).invoke(AgentRef.a2a("unknown", "x"), "msg", context))
                .hasMessageContaining("not configured");
    }

    @Test
    void aWorkflowRunPassesItsScopeToLocalAgentsAndOnlyTheRunIdToA2AAgents() {
        var scope = new nl.metafactory.agents.workflowrun.WorkflowRunScope("wf-1", "run-7", null);
        var bound = new WorkflowAgentInvoker.Invocation("run-7", "acme", "alice", scope);
        when(runner.run(any(), any(), any(), any(), any(), eq(scope))).thenReturn("wrote files");
        assertThat(invoker(runner).invoke(AgentRef.local("dev"), "msg", bound)).isEqualTo("wrote files");

        when(providers.find("litellm")).thenReturn(Optional.of(litellm));
        when(litellm.sendMessage(eq("software-developer"), eq("msg"), isNull(),
                eq(java.util.Map.of("x-openjcockpit-workflow-run", "run-7"))))
                .thenReturn(new A2AProviderClient.MessageResult(Status.OK, "done", null));
        assertThat(invoker(runner).invoke(AgentRef.a2a("litellm", "software-developer"), "msg", bound)).isEqualTo("done");
    }

    @Test
    void a2aAgentsAreCalledWithTheProvidersCallerIdentity() {
        when(providers.find("acme-gw")).thenReturn(Optional.of(litellm));
        when(litellm.callerAuthorization()).thenReturn(Optional.of("Bearer service-token"));
        when(litellm.sendMessage(eq("qa-engineer"), eq("msg"), eq("Bearer service-token"), eq(java.util.Map.of())))
                .thenReturn(new A2AProviderClient.MessageResult(Status.OK, "verified", null));
        assertThat(invoker(runner).invoke(AgentRef.a2a("acme-gw", "qa-engineer"), "msg", context)).isEqualTo("verified");

        when(litellm.name()).thenReturn("Acme");
        when(litellm.callerAuthorization()).thenThrow(new IllegalStateException("token endpoint down"));
        assertThatThrownBy(() -> invoker(runner).invoke(AgentRef.a2a("acme-gw", "qa-engineer"), "msg", context))
                .isInstanceOf(AgentInvocationException.class).hasMessageContaining("caller token")
                .hasMessageNotContaining("token endpoint down");
    }
}
