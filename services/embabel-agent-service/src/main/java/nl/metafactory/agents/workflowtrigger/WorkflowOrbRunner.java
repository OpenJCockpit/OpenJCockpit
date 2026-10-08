package nl.metafactory.agents.workflowtrigger;

import nl.metafactory.agents.config.WorkflowTriggerProperties;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.orchestration.AgentRunStore;
import nl.metafactory.agents.orchestration.PipelineState;
import nl.metafactory.agents.orchestration.RunStatuses;
import nl.metafactory.agents.workflow.ChildStartCommand;
import nl.metafactory.agents.workflow.ChildStartDecision;
import nl.metafactory.agents.workflow.ChildWorkflowStarter;
import nl.metafactory.agents.workflow.WorkflowChainResolver;
import nl.metafactory.agents.workflow.WorkflowDefinitionRepository;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowOrb;
import nl.metafactory.agents.workflow.model.WorkflowOrbMode;
import nl.metafactory.agents.workflow.model.WorkflowStartResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Evaluates and starts workflow orbs anchored to a given pipeline stage. */
@Component
public class WorkflowOrbRunner {

    private static final String ORB_AGENT_ID = "workflow-orb";
    private static final String WORKFLOW_PREFIX = "workflow:";
    private static final Logger log = LoggerFactory.getLogger(WorkflowOrbRunner.class);

    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final WorkflowChainResolver workflowChainResolver;
    private final WorkflowTriggerProperties workflowTriggerProperties;
    private final ChildWaitRegistry childWaitRegistry;
    private final DeadlineScheduler deadlineScheduler;
    private final WorkflowTriggerCoordinator workflowTriggerCoordinator;
    private final AgentRunStore agentRunStore;
    private final ObjectProvider<ChildWorkflowStarter> childWorkflowStarterProvider;

    public WorkflowOrbRunner(WorkflowDefinitionRepository workflowDefinitionRepository,
                              WorkflowChainResolver workflowChainResolver,
                              WorkflowTriggerProperties workflowTriggerProperties,
                              ChildWaitRegistry childWaitRegistry,
                              DeadlineScheduler deadlineScheduler,
                              WorkflowTriggerCoordinator workflowTriggerCoordinator,
                              AgentRunStore agentRunStore,
                              ObjectProvider<ChildWorkflowStarter> childWorkflowStarterProvider) {
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.workflowChainResolver = workflowChainResolver;
        this.workflowTriggerProperties = workflowTriggerProperties;
        this.childWaitRegistry = childWaitRegistry;
        this.deadlineScheduler = deadlineScheduler;
        this.workflowTriggerCoordinator = workflowTriggerCoordinator;
        this.agentRunStore = agentRunStore;
        this.childWorkflowStarterProvider = childWorkflowStarterProvider;
    }

    /** Runs every not-yet-started orb anchored to stageId in declaration order. Returns true if the parent parked or was blocked. */
    public boolean runOrbsAnchoredTo(PipelineState state, String stageId) {
        String parentRunId = state.runId();
        String parentWorkflowId = parseWorkflowId(state.request().requestedBy());
        if (parentWorkflowId == null) {
            return false;
        }
        WorkflowDefinition parentDefinition = workflowDefinitionRepository.findById(parentWorkflowId).orElse(null);
        if (parentDefinition == null || parentDefinition.workflowOrbs() == null) {
            return false;
        }
        List<WorkflowOrb> orbs = parentDefinition.workflowOrbs();
        for (int index = 0; index < orbs.size(); index++) {
            WorkflowOrb orb = orbs.get(index);
            String orbKey = "orb:" + index;
            String placementStage = blankToNull(orb.placementStage());
            if (!Objects.equals(placementStage, stageId) || state.orbDone(orbKey)) {
                continue;
            }
            if (!workflowTriggerProperties.isEnabled()) {
                agentRunStore.recordEvent(parentRunId, ORB_AGENT_ID,
                        "Workflow orb '" + orb.workflowId() + "' skipped - workflow-trigger feature is disabled",
                        "SKIPPED", "");
                state.markOrbCompleted(orbKey);
                continue;
            }
            if (processOrb(state, parentRunId, parentDefinition, orb, orbKey, stageId)) {
                return true;
            }
        }
        return false;
    }

    private boolean processOrb(PipelineState state, String parentRunId, WorkflowDefinition parentDefinition,
                                WorkflowOrb orb, String orbKey, String stageId) {
        ChildStartDecision decision = workflowChainResolver.resolve(orb.workflowId(), parentDefinition,
                state.request().chainAncestry());
        if (decision instanceof ChildStartDecision.Refused refused) {
            log.info("workflow.child.refused parentRunId={} childWorkflowId={} code={} reason={}",
                    parentRunId, orb.workflowId(), refused.code(), refused.reason());
            agentRunStore.recordEvent(parentRunId, ORB_AGENT_ID,
                    "Workflow orb refused: " + refused.code() + " - " + refused.reason(), "BLOCKED", "");
            if (orb.mode() == WorkflowOrbMode.SEQUENTIAL) {
                agentRunStore.setStatus(parentRunId, "BLOCKED");
                return true;
            }
            return false;
        }

        WorkflowDefinition target = ((ChildStartDecision.Permitted) decision).target();
        ChildStartCommand command = buildCommand(state, orb, target);
        ChildWorkflowStarter starter = childWorkflowStarterProvider.getObject();
        WorkflowStartResponse response = starter.startWorkflowFromOrb(command);

        if (response.executionId() == null) {
            agentRunStore.recordEvent(parentRunId, ORB_AGENT_ID,
                    "Child workflow " + orb.workflowId() + " did not start: " + response.message(), "BLOCKED", "");
            if (orb.mode() == WorkflowOrbMode.SEQUENTIAL) {
                agentRunStore.setStatus(parentRunId, "BLOCKED");
                return true;
            }
            return false;
        }

        agentRunStore.recordEvent(parentRunId, ORB_AGENT_ID,
                "Started child workflow " + target.name() + " (" + orb.mode() + ")", response.status(),
                "run://" + response.executionId());
        String initiatorUsername = state.request().initiator() != null ? state.request().initiator().username() : null;
        log.info("workflow.child.started parentRunId={} childRunId={} childWorkflowId={} mode={} anchorStage={} initiator={}",
                parentRunId, response.executionId(), orb.workflowId(), orb.mode(), stageId, initiatorUsername);
        state.markOrbCompleted(orbKey);

        if (orb.mode() == WorkflowOrbMode.SEQUENTIAL) {
            var deadline = deadlineScheduler.schedule(
                    () -> workflowTriggerCoordinator.onDeadlineExpired(parentRunId),
                    workflowTriggerProperties.getSequentialMaxWait());
            agentRunStore.setStatus(parentRunId, RunStatuses.AWAITING_CHILD_WORKFLOW);
            childWaitRegistry.park(parentRunId, new ChildWait(state, response.executionId(), orb.workflowId(),
                    target.name(), stageId, Instant.now(), deadline));
            var childRun = agentRunStore.get(response.executionId());
            if (childRun != null && RunStatuses.isTerminal(childRun.status())) {
                workflowTriggerCoordinator.onRunTerminal(new RunTerminalEvent(response.executionId(), childRun.status()));
            }
            return true;
        }
        return false;
    }

    private ChildStartCommand buildCommand(PipelineState state, WorkflowOrb orb, WorkflowDefinition target) {
        AgentRunRequest parentRequest = state.request();
        return new ChildStartCommand(orb.workflowId(), parentRequest, orb.mode(), parentRequest.initiator(),
                parentRequest.chainAncestry());
    }

    private static String parseWorkflowId(String requestedBy) {
        if (requestedBy == null || !requestedBy.startsWith(WORKFLOW_PREFIX)
                || requestedBy.length() <= WORKFLOW_PREFIX.length()) {
            return null;
        }
        return requestedBy.substring(WORKFLOW_PREFIX.length());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
