package nl.metafactory.agents.workflow.agents;

import nl.metafactory.agents.definition.runtime.AgentRunService;
import nl.metafactory.agents.definition.runtime.ConfiguredDefinitionAgent;

/**
 * Runs agents of the repository {@code /agents} folder in-process with the configuration-driven
 * runtime (same tenant binding, tool allowlist and guardrails as served agents). Created by
 * {@code AgentDefinitionConfiguration}; absent when that runtime is disabled.
 */
public class LocalCatalogAgentRunner {

    private final AgentRunService runService;

    public LocalCatalogAgentRunner(AgentRunService runService) {
        this.runService = runService;
    }

    public ConfiguredDefinitionAgent.Response run(String tenant, String agentId, String principal, String message) {
        return run(tenant, agentId, principal, message, null);
    }

    public ConfiguredDefinitionAgent.Response run(String tenant, String agentId, String principal, String message,
                                                  nl.metafactory.agents.workflowrun.WorkflowRunScope workflow) {
        return runService.run(tenant, agentId, principal, null, message, workflow);
    }
}
