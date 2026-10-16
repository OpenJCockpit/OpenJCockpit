package nl.metafactory.agents.workflow.agents;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Test double of the workflow_agent_refs linking table; validates keys like the JDBC version. */
public class InMemoryWorkflowAgentLinks implements WorkflowAgentLinks {

    private final Map<String, List<String>> links = new ConcurrentHashMap<>();

    @Override
    public List<String> find(String workflowId) {
        return workflowId == null ? List.of() : links.getOrDefault(workflowId, List.of());
    }

    @Override
    public void replace(String workflowId, List<String> keys) {
        for (String key : keys) {
            if (!AgentRef.isRef(key)) {
                throw new IllegalArgumentException("not an agent reference: " + key);
            }
        }
        if (keys.isEmpty()) {
            links.remove(workflowId);
        } else {
            links.put(workflowId, List.copyOf(keys));
        }
    }

    @Override
    public void delete(String workflowId) {
        links.remove(workflowId);
    }
}
