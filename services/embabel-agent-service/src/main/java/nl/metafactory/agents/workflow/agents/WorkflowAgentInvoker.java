package nl.metafactory.agents.workflow.agents;

import nl.metafactory.agents.workflowrun.WorkflowRunScope;
import nl.metafactory.agents.workflowrun.WorkflowRunScopes;

import nl.metafactory.agents.a2a.client.A2AProviderClient;
import nl.metafactory.agents.a2a.client.A2AProviderClients;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Runs one agent a workflow references and returns its text output. Local agents run on the agent runtime
 * ({@link LocalCatalogAgentRunner}); A2A agents run at their provider through that provider's single
 * {@link A2AProviderClient}, authenticated with the provider's caller identity (client credentials).
 * Nothing runs in this process.
 */
@Component
public class WorkflowAgentInvoker {

    private final LocalCatalogAgentRunner localRunner;
    private final A2AProviderClients providers;
    private final AgentWorkflowProperties properties;

    public WorkflowAgentInvoker(LocalCatalogAgentRunner localRunner, A2AProviderClients providers,
                                AgentWorkflowProperties properties) {
        this.localRunner = localRunner;
        this.providers = providers;
        this.properties = properties;
    }

    /**
     * Run correlation and identity for one agent invocation; never contains message content.
     * {@code workflow} is the workflow run (and its git workspace, if any) the agent runs in, or null.
     */
    public record Invocation(String runId, String customerId, String principal, WorkflowRunScope workflow) {
        public Invocation(String runId, String customerId, String principal) {
            this(runId, customerId, principal, null);
        }
    }

    public String invoke(AgentRef ref, String message, Invocation context) {
        return ref.source() == AgentRef.Source.LOCAL ? invokeLocal(ref, message, context) : invokeA2A(ref, message, context);
    }

    private String invokeLocal(AgentRef ref, String message, Invocation context) {
        String tenant = properties.getTenant() != null && !properties.getTenant().isBlank()
                ? properties.getTenant() : context.customerId();
        // one id per invocation (a feedback re-run is a new invocation); the runtime runs an id at most once
        String executionId = (context.runId() == null ? "" : context.runId() + ":") + UUID.randomUUID();
        return localRunner.run(executionId, tenant, ref.agentId(),
                context.principal() == null ? "workflow" : context.principal(), message, context.workflow());
    }

    private String invokeA2A(AgentRef ref, String message, Invocation context) {
        var client = providers.find(ref.provider())
                .orElseThrow(() -> new AgentInvocationException("A2A provider " + ref.provider() + " is not configured"));
        String authorization;
        try {
            authorization = client.callerAuthorization().orElse(null);
        } catch (RuntimeException e) {
            throw new AgentInvocationException("Could not obtain a caller token for A2A provider " + client.name());
        }
        // A workflow run forwards only its run id; the agent service resolves workflow and git workspace from
        // its own registry (WorkflowRunScopes), so no caller can name either.
        Map<String, String> forwarded = context != null && context.workflow() != null
                ? Map.of(WorkflowRunScopes.RUN_HEADER, context.workflow().workflowRunId()) : Map.of();
        var result = client.sendMessage(ref.agentId(), message, authorization, forwarded);
        if (result.status() != A2AProviderClient.Status.OK) {
            throw new AgentInvocationException("A2A agent " + ref.agentId() + " at " + client.name() + " failed: "
                    + result.status() + (result.error() == null ? "" : " " + result.error()));
        }
        return result.text();
    }
}
