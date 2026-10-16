package nl.metafactory.agents.workflowrun;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Active workflow runs of this instance, keyed by run id. A2A-served agents receive only the run id
 * (forwarded by LiteLLM as a header) and look their scope up here, so a caller can never name a workflow
 * or a git workspace itself; an unknown or finished run has no scope. In-memory, like parked runs
 * (ADR-001): lost on restart.
 */
@Component
public class WorkflowRunScopes {

    /** Header with the workflow run id; sent to A2A agents as {@code x-a2a-<agent>-x-openjcockpit-workflow-run}. */
    public static final String RUN_HEADER = "x-openjcockpit-workflow-run";

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
