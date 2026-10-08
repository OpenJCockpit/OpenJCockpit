package nl.metafactory.agents.workflowtrigger;

import nl.metafactory.agents.orchestration.AgentOrchestrator;
import nl.metafactory.agents.orchestration.AgentRunStore;
import nl.metafactory.agents.orchestration.PipelineContinuation;
import nl.metafactory.agents.orchestration.RunStatuses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Resolves the three-way race between child completion, wait-budget expiry, and operator stop. */
@Component
public class WorkflowTriggerCoordinator {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTriggerCoordinator.class);

    private static final String ORB_AGENT_ID = "workflow-orb";

    private final ChildWaitRegistry childWaitRegistry;
    private final AgentRunStore agentRunStore;
    private final ObjectProvider<PipelineContinuation> pipelineContinuationProvider;
    private final ObjectProvider<AgentOrchestrator> agentOrchestratorProvider;

    public WorkflowTriggerCoordinator(ChildWaitRegistry childWaitRegistry, AgentRunStore agentRunStore,
                                       ObjectProvider<PipelineContinuation> pipelineContinuationProvider,
                                       ObjectProvider<AgentOrchestrator> agentOrchestratorProvider) {
        this.childWaitRegistry = childWaitRegistry;
        this.agentRunStore = agentRunStore;
        this.pipelineContinuationProvider = pipelineContinuationProvider;
        this.agentOrchestratorProvider = agentOrchestratorProvider;
    }

    /**
     * Reacts to a child run reaching a terminal status. Wrapped entirely in a try/catch because
     * completeRun sits inside pipeline()'s own try block, and an uncaught exception here would be
     * caught by that outer catch and overwrite an already-COMPLETED child's status with FAILED.
     */
    @EventListener
    public void onRunTerminal(RunTerminalEvent event) {
        try {
            ChildWait claimed = childWaitRegistry.claimByChild(event.runId());
            if (claimed == null) {
                return;
            }
            cancelDeadline(claimed);
            String parentRunId = claimed.parentState().runId();
            long waitedMs = java.time.Duration.between(claimed.parkedAt(), java.time.Instant.now()).toMillis();
            log.info("workflow.child.completed parentRunId={} childRunId={} childStatus={} waitedMs={}",
                    parentRunId, event.runId(), event.status(), waitedMs);
            agentRunStore.recordEvent(parentRunId, ORB_AGENT_ID,
                    "Child workflow " + claimed.childWorkflowName() + " (" + event.runId() + ") reached " + event.status(),
                    event.status(), "run://" + event.runId());
            if (RunStatuses.COMPLETED.equals(event.status())) {
                pipelineContinuationProvider.getObject().resume(claimed.parentState());
            } else {
                agentRunStore.setStatus(parentRunId, event.status());
            }
        } catch (Exception e) {
            log.error("workflow-trigger.child-terminal.failed runId={}", event.runId(), e);
        }
    }

    /** Reacts to the wait budget expiring for parentRunId. The child is never cancelled. */
    public void onDeadlineExpired(String parentRunId) {
        ChildWait claimed = childWaitRegistry.claim(parentRunId);
        if (claimed == null) {
            return;
        }
        long budgetMs = java.time.Duration.between(claimed.parkedAt(), java.time.Instant.now()).toMillis();
        log.info("workflow.child.timeout parentRunId={} childRunId={} budget={}", parentRunId, claimed.childRunId(), budgetMs);
        agentRunStore.recordEvent(parentRunId, ORB_AGENT_ID,
                "Wait budget expired for child workflow " + claimed.childWorkflowName() + " (" + claimed.childRunId() + ")",
                RunStatuses.TIMED_OUT, "run://" + claimed.childRunId());
        agentRunStore.setStatus(parentRunId, RunStatuses.TIMED_OUT);
    }

    /** Cascades an operator stop of parentRunId to its parked sequential child, if any. */
    public void onParentStopped(String parentRunId) {
        ChildWait claimed = childWaitRegistry.claim(parentRunId);
        if (claimed == null) {
            return;
        }
        cancelDeadline(claimed);
        log.info("workflow.child.cancelled parentRunId={} childRunId={}", parentRunId, claimed.childRunId());
        agentRunStore.recordEvent(parentRunId, ORB_AGENT_ID,
                "Cancelling child workflow " + claimed.childWorkflowName() + " (" + claimed.childRunId() + ") because the parent was stopped",
                "CANCELLED", "run://" + claimed.childRunId());
        agentRunStore.setStatus(parentRunId, "CANCELLED");
        AgentOrchestrator orchestrator = agentOrchestratorProvider.getObject();
        orchestrator.stop(claimed.childRunId());
    }

    private void cancelDeadline(ChildWait claimed) {
        if (claimed.deadline() != null) {
            claimed.deadline().cancel(false);
        }
    }
}
