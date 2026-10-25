package nl.metafactory.agents.workflow.agents;

import nl.metafactory.agents.runtime.AgentRuntimeClient;
import nl.metafactory.agents.workflowrun.WorkflowRunScope;
import nl.metafactory.common.agentruntime.AgentExecutionFailure;
import nl.metafactory.common.agentruntime.AgentExecutionRequest;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Runs agents of the repository {@code /agents} folder on the agent runtime (same tenant binding, tool
 * allowlist and guardrails as served agents): the catalog's definition snapshot and the run's id go with
 * the request; the runtime resolves the run's scope from this service. One request per invocation, never
 * retried.
 */
@Component
public class LocalCatalogAgentRunner {

    /** Failures that are not about the definition: reported as "failed", with the runtime's safe message. */
    private static final Set<String> EXECUTION_FAILURES = Set.of(AgentRuntimeClient.RUNTIME_UNAVAILABLE,
            AgentExecutionFailure.EXECUTION_FAILED, AgentExecutionFailure.GUARDRAIL_VIOLATION,
            AgentExecutionFailure.DUPLICATE_EXECUTION, AgentExecutionFailure.INVALID_REQUEST,
            AgentExecutionFailure.FORBIDDEN);

    private final CatalogAgentDefinitions definitions;
    private final AgentRuntimeClient runtime;

    public LocalCatalogAgentRunner(CatalogAgentDefinitions definitions, AgentRuntimeClient runtime) {
        this.definitions = definitions;
        this.runtime = runtime;
    }

    /**
     * @param executionId unique per invocation; the runtime runs an id at most once
     * @return the agent's answer text
     */
    public String run(String executionId, String tenant, String agentId, String principal, String message,
                      WorkflowRunScope workflow) {
        var request = new AgentExecutionRequest(executionId, tenant, agentId, principal,
                workflow == null ? null : workflow.workflowRunId(), null, message, definitions.lookup(agentId));
        try {
            return runtime.execute(request).text();
        } catch (AgentRuntimeClient.AgentRuntimeException e) {
            throw new AgentInvocationException(EXECUTION_FAILURES.contains(e.reason())
                    ? "Local agent " + agentId + " failed: " + e.getMessage()
                    : "Local agent " + agentId + " not runnable: " + e.reason());
        }
    }
}
