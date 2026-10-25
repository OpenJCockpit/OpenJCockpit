package nl.metafactory.agentruntime.workflowrun;

import nl.metafactory.agentruntime.management.AgentServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Looks up the scope of an active workflow run in the workflow engine's registry (agent service). A caller
 * only ever names a run id; workflow and git workspace come from the engine. Empty for an unknown or
 * finished run; {@link AgentServiceClient.ManagementUnavailableException} when the engine cannot be asked
 * or reports an invalid scope, so the caller decides how to fail closed.
 */
public class WorkflowRunScopeResolver {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRunScopeResolver.class);

    private final AgentServiceClient agentService;

    public WorkflowRunScopeResolver(AgentServiceClient agentService) {
        this.agentService = agentService;
    }

    public Optional<WorkflowRunScope> find(String runId) {
        if (runId == null || runId.isBlank()) {
            return Optional.empty();
        }
        var descriptor = agentService.workflowRun(runId);
        if (descriptor.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (!runId.equals(descriptor.get().workflowRunId())) {
                throw new IllegalArgumentException("workflow run id mismatch");
            }
            return Optional.of(WorkflowRunScope.from(descriptor.get()));
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("workflow-run.scope.rejected runId={} reason={}", runId, e.getClass().getSimpleName());
            throw new AgentServiceClient.ManagementUnavailableException("invalid workflow run scope", e);
        }
    }
}
