package nl.metafactory.agents.workflowrun;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Active workflow runs of this instance, keyed by run id. Agents receive only the run id (A2A: forwarded by
 * LiteLLM as a header; catalog agents: in the execution request) and the agent runtime looks their scope up
 * here through the internal API, so a caller can never name a workflow or a git workspace itself; an
 * unknown or finished run has no scope. In-memory; the scope of a run parked at an approval gate is part of
 * its persisted snapshot and is registered again on startup by {@code ParkedRunRehydrator}.
 */
@Component
public class WorkflowRunScopes {

    /** Header with the workflow run id; sent to A2A agents as {@code x-a2a-<agent>-x-openjcockpit-workflow-run}. */
    public static final String RUN_HEADER = nl.metafactory.common.agentruntime.AgentRuntimeApi.WORKFLOW_RUN_HEADER;

    private final Map<String, WorkflowRunScope> byRun = new ConcurrentHashMap<>();

    public void register(WorkflowRunScope scope) {
        byRun.put(scope.workflowRunId(), scope);
    }

    public Optional<WorkflowRunScope> find(String runId) {
        return runId == null ? Optional.empty() : Optional.ofNullable(byRun.get(runId));
    }

    public void remove(String runId) {
        if (runId != null) {
            byRun.remove(runId);
        }
    }
}
