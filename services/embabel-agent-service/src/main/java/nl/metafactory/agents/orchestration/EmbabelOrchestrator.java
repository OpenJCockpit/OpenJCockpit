package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.approval.ApprovalGateCoordinator;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.approval.StageChangeReports;
import nl.metafactory.agents.approval.model.StageChangeReport;
import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.gitworkspace.GitWorkspaceBinding;
import nl.metafactory.agents.workflowrun.WorkflowRunScope;
import nl.metafactory.agents.workflowrun.WorkflowRunScopes;
import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.workflow.DefinitionFileReadException;
import nl.metafactory.agents.workflow.agents.AgentInvocationException;
import nl.metafactory.agents.workflow.agents.AgentRef;
import nl.metafactory.agents.workflow.agents.AgentWorkflowProperties;
import nl.metafactory.agents.workflow.agents.WorkflowAgentInvoker;
import nl.metafactory.agents.workflowtrigger.WorkflowOrbRunner;
import nl.metafactory.agents.workflowtrigger.WorkflowTriggerCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;

/**
 * The workflow execution engine — the only one in the platform. A run executes the agents a
 * workflow references (central agent catalog: local {@code /agents} folder or an A2A provider) as a
 * sequential chain. Runs are re-entrant (ADR-001): approval gates park a run after a referenced
 * agent and {@link #resume} continues it; workflow orbs anchor to agent references.
 */
@Service
public class EmbabelOrchestrator implements AgentOrchestrator, PipelineContinuation {

    private static final Logger log = LoggerFactory.getLogger(EmbabelOrchestrator.class);
    private static final String WORKFLOW_DEFINITION_AGENT_ID = "workflow-definition";

    private final AsyncPipelineRunner asyncPipelineRunner;
    private final AgentRunStore runStore;
    private final ApprovalGateCoordinator gateCoordinator;
    private final ApprovalGateRegistry gateRegistry;
    private final WorkflowOrbRunner workflowOrbRunner;
    private final WorkflowTriggerCoordinator workflowTriggerCoordinator;
    private final WorkflowAgentInvoker agentInvoker;
    private final AgentWorkflowProperties agentWorkflowProperties;
    private final AgentRunOutputs agentRunOutputs;
    private final WorkflowRunScopes runScopes;
    private final RunCheckpoints checkpoints;

    public EmbabelOrchestrator(AsyncPipelineRunner asyncPipelineRunner,
                               AgentRunStore runStore,
                               ApprovalGateCoordinator gateCoordinator,
                               ApprovalGateRegistry gateRegistry,
                               WorkflowOrbRunner workflowOrbRunner,
                               WorkflowTriggerCoordinator workflowTriggerCoordinator,
                               WorkflowAgentInvoker agentInvoker,
                               AgentWorkflowProperties agentWorkflowProperties,
                               AgentRunOutputs agentRunOutputs) {
        this(asyncPipelineRunner, runStore, gateCoordinator, gateRegistry, workflowOrbRunner, workflowTriggerCoordinator,
                agentInvoker, agentWorkflowProperties, agentRunOutputs, new WorkflowRunScopes());
    }

    public EmbabelOrchestrator(AsyncPipelineRunner asyncPipelineRunner,
                               AgentRunStore runStore,
                               ApprovalGateCoordinator gateCoordinator,
                               ApprovalGateRegistry gateRegistry,
                               WorkflowOrbRunner workflowOrbRunner,
                               WorkflowTriggerCoordinator workflowTriggerCoordinator,
                               WorkflowAgentInvoker agentInvoker,
                               AgentWorkflowProperties agentWorkflowProperties,
                               AgentRunOutputs agentRunOutputs,
                               WorkflowRunScopes runScopes) {
        this(asyncPipelineRunner, runStore, gateCoordinator, gateRegistry, workflowOrbRunner, workflowTriggerCoordinator,
                agentInvoker, agentWorkflowProperties, agentRunOutputs, runScopes, RunCheckpoints.NONE);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EmbabelOrchestrator(AsyncPipelineRunner asyncPipelineRunner,
                               AgentRunStore runStore,
                               ApprovalGateCoordinator gateCoordinator,
                               ApprovalGateRegistry gateRegistry,
                               WorkflowOrbRunner workflowOrbRunner,
                               WorkflowTriggerCoordinator workflowTriggerCoordinator,
                               WorkflowAgentInvoker agentInvoker,
                               AgentWorkflowProperties agentWorkflowProperties,
                               AgentRunOutputs agentRunOutputs,
                               WorkflowRunScopes runScopes,
                               RunCheckpoints checkpoints) {
        this.runScopes = runScopes;
        this.checkpoints = checkpoints;
        this.asyncPipelineRunner = asyncPipelineRunner;
        this.runStore = runStore;
        this.gateCoordinator = gateCoordinator;
        this.gateRegistry = gateRegistry;
        this.workflowOrbRunner = workflowOrbRunner;
        this.workflowTriggerCoordinator = workflowTriggerCoordinator;
        this.agentInvoker = agentInvoker;
        this.agentWorkflowProperties = agentWorkflowProperties;
        this.agentRunOutputs = agentRunOutputs;
    }

    /** Starts a workflow run. Every entry of {@code agentIds} must be an agent reference. */
    @Override
    public AgentRun start(AgentRunRequest request) {
        return start(request, null);
    }

    /**
     * Starts a workflow run; with {@code gitWorkspace} the run is bound to that git workspace (registered
     * before the pipeline starts, released when the run ends).
     */
    @Override
    public AgentRun start(AgentRunRequest request, GitWorkspaceBinding gitWorkspace) {
        if (request.agentIds() == null || request.agentIds().isEmpty()
                || !request.agentIds().stream().allMatch(AgentRef::isRef)) {
            throw new IllegalArgumentException("A run needs at least one agent and only agent references");
        }
        var runId = UUID.randomUUID().toString();
        var repoUrl = request.repositoryUrl() != null ? request.repositoryUrl() : "";
        var run = runStore.create(runId, request.customerId(), request.specFile(), repoUrl, request.workflowId(), request.startedBy());
        var spec = new SpecContent(runId, "spec.md", request.specFile(), repoUrl);
        var state = new PipelineState(runId, spec, Set.copyOf(request.agentIds()), request);
        // Every run is a workflow run: its agents carry workflow + run id (OPA attribution of tool calls) and,
        // for runs on a project, the git workspace ai-control prepared. Registered before the pipeline starts.
        var scope = new WorkflowRunScope(request.workflowId(), runId, gitWorkspace);
        state.setWorkflowScope(scope);
        runScopes.register(scope);
        if (gitWorkspace != null) {
            log.info("workflow.run.git-workspace runId={} workspace={}", runId, gitWorkspace.workspaceKey());
        }
        asyncPipelineRunner.run(() -> pipeline(state));
        return run;
    }

    @Override
    public AgentRun get(String runId) {
        var run = runStore.get(runId);
        if (run != null) {
            return run;
        }
        // An id unknown to this instance (never seen, or a parked run lost to a restart) is a
        // defined, terminal outcome; it never carries a fabricated failure summary.
        return new AgentRun(runId, "unknown", "unknown", "", "RUN_STATE_LOST", Instant.now(), List.of(), List.of(), null, null, null, null);
    }

    @Override
    public void stop(String runId) {
        runStore.markCancelled(runId);
        // A parked run has no thread left to observe checkCancelled (ADR-001), so Stop on a
        // parked run must act synchronously here rather than merely setting a flag (AC-34).
        PipelineState parked = gateRegistry.evict(runId);
        if (parked != null) {
            runStore.setStatus(runId, "CANCELLED");
            runScopes.remove(runId);
        }
        workflowTriggerCoordinator.onParentStopped(runId);
    }

    /** {@link PipelineContinuation} — resumes a parked run from wherever it left off (ADR-001). */
    @Override
    public void resume(PipelineState state) {
        runStore.setStatus(state.runId(), "RUNNING");
        asyncPipelineRunner.run(() -> pipeline(state));
    }

    /**
     * Runs the workflow's referenced agents in order (sequential chain). Each agent receives the
     * workflow input plus the outputs of the agents before it; its output is recorded as run evidence.
     * Approval gates and workflow orbs anchor to agent reference keys; reviewer feedback re-runs the
     * anchored agent with the comments.
     */
    void pipeline(PipelineState s) {
        String runId = s.runId();
        try {
            if (workflowOrbRunner.runOrbsAnchoredTo(s, null)) return;
            List<String> chain = s.request().agentIds();
            for (int position = 0; position < chain.size(); position++) {
                String key = chain.get(position);
                if (s.isDone(key)) {
                    continue;
                }
                checkCancelled(runId);
                AgentRef ref = AgentRef.parse(key).orElseThrow();
                String feedback = takeFeedbackFor(s, key);
                recordEvent(runId, key, feedback == null ? "Running agent " + key : "Re-running agent " + key
                        + " with reviewer feedback", "RUNNING", "");
                String output;
                try {
                    output = agentInvoker.invoke(ref, composeMessage(s, key, feedback),
                            new WorkflowAgentInvoker.Invocation(runId, s.request().customerId(), s.request().startedBy(),
                                    s.workflowScope()));
                } catch (AgentInvocationException e) {
                    recordEvent(runId, key, e.getMessage(), "FAILED", "");
                    completeRun(runId, "FAILED", "Agent " + key + " failed");
                    return;
                }
                output = output == null ? "" : output;
                s.agentOutputs.put(key, output);
                agentRunOutputs.record(runId, key, position, output);
                recordEvent(runId, key, truncate(output, OUTPUT_EVENT_CHARS), "OK", "agent-output://" + key);
                // Git-workspace runs: commit + push this agent's work before the next agent starts.
                checkpoints.afterAgent(s.workflowScope(), key, position, chain.size(), output)
                        .ifPresent(outcome -> recordEvent(runId, key, outcome, "OK", ""));
                if (endStage(s, key, StageChangeReports.notApplicable("Agent " + key + " produced "
                        + output.length() + " characters of output"))) return;
                if (workflowOrbRunner.runOrbsAnchoredTo(s, key)) return;
            }
            completeRun(runId, "COMPLETED");
        } catch (CancellationException e) {
            completeRun(runId, "CANCELLED");
        } catch (Exception e) {
            log.error("Workflow run {} failed", runId, e);
            if (e instanceof DefinitionFileReadException definitionFailure) {
                recordEvent(runId, WORKFLOW_DEFINITION_AGENT_ID,
                        "Workflow definition file could not be read: " + definitionFailure.relativeName()
                                + " — " + definitionFailure.sanitisedReason(),
                        "FAILED", "");
            }
            completeRun(runId, "FAILED", RunFailureDiagnostics.summarise(e));
        }
    }

    static final int OUTPUT_EVENT_CHARS = 1_900;

    private static String takeFeedbackFor(PipelineState s, String key) {
        var gate = s.gate();
        if (gate == null || !key.equals(gate.placementStage()) || gate.pendingFeedback() == null) {
            return null;
        }
        String feedback = gate.pendingFeedback();
        gate.setPendingFeedback(null);
        return feedback;
    }

    /** Workflow input, then previous outputs (newest kept first when over budget), then feedback. */
    String composeMessage(PipelineState s, String key, String feedback) {
        int budget = agentWorkflowProperties.getMaxMessageChars();
        String input = s.spec().content() == null ? "" : s.spec().content();
        var head = new StringBuilder("# Workflow input\n").append(truncate(input, budget / 3)).append("\n");
        String tail = feedback == null ? "" : "\n# Reviewer feedback on your previous output\n" + truncate(feedback, budget / 6)
                + (s.agentOutputs.containsKey(key) ? "\n\n# Your previous output\n" + truncate(s.agentOutputs.get(key), budget / 6) : "");
        var previous = new java.util.ArrayList<String>();
        int remaining = budget - head.length() - tail.length();
        var keys = new java.util.ArrayList<>(s.agentOutputs.keySet());
        java.util.Collections.reverse(keys);
        for (String k : keys) {
            if (k.equals(key)) {
                continue;
            }
            String section = "\n# Output of " + k + "\n" + s.agentOutputs.get(k) + "\n";
            if (section.length() > remaining) {
                break;
            }
            previous.add(0, section);
            remaining -= section.length();
        }
        return head + String.join("", previous) + tail;
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 15)) + "… [truncated]";
    }

    private boolean endStage(PipelineState s, String stageId, StageChangeReport report) {
        boolean firstPass = s.markCompleted(stageId);
        return firstPass && gateCoordinator.pauseAfter(s, stageId, report);
    }

    private void checkCancelled(String runId) {
        if (runStore.isCancelled(runId)) {
            throw new CancellationException("Run cancelled: " + runId);
        }
    }

    private void recordEvent(String runId, String agentId, String title, String status, String evidenceRef) {
        runStore.recordEvent(runId, agentId, title, status, evidenceRef);
    }

    private void completeRun(String runId, String finalStatus) {
        completeRun(runId, finalStatus, null);
    }

    private void completeRun(String runId, String finalStatus, String failureSummary) {
        runScopes.remove(runId);
        runStore.setStatus(runId, finalStatus, failureSummary);
    }
}
