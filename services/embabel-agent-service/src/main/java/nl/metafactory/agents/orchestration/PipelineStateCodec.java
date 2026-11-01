package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.approval.model.ApprovalGateState;
import nl.metafactory.agents.approval.model.GateComment;
import nl.metafactory.agents.approval.model.StageChangeReport;
import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.workflowrun.WorkflowRunScope;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JSON snapshot of a parked {@link PipelineState} (table {@code agent_parked_runs}), so a run paused at
 * an approval gate survives a restart. Everything a resumed {@code pipeline()} pass reads is in the
 * snapshot: the request, spec, completed stages/orbs, the agent outputs so far, the gate (iterations,
 * decisions, comments, last report) and the run's {@link WorkflowRunScope} (workflow, run id and git
 * workspace binding — identifiers only).
 *
 * <p>Deliberately NOT persisted: the start request's {@code gitToken}. The engine never uses it (agents
 * reach git through the git MCP server's workspace, which resolves its own credentials); the only
 * reader is a child workflow started by a workflow orb, which after a restart receives none.</p>
 *
 * <p>The snapshot carries {@link #FORMAT_VERSION}; a snapshot in any other format is refused
 * ({@link IllegalStateException}) so the rehydrator fails that run with a clear reason instead of
 * resuming it from misread state.</p>
 */
public final class PipelineStateCodec {

    static final int FORMAT_VERSION = 1;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    record Snapshot(int formatVersion, String runId, SpecContent spec, List<String> selectedAgentIds,
                    AgentRunRequest request, List<String> completedStages, List<String> completedOrbs,
                    Map<String, String> agentOutputs, Gate gate, WorkflowRunScope workflowScope) {
    }

    record Gate(String placementStage, boolean feedbackSupported, int maxFeedbackIterations, int iteration,
                boolean open, boolean acceptedFinal, List<Integer> decidedIterations, List<GateComment> commentHistory,
                String pendingFeedback, StageChangeReport lastReport, String retainedBranch,
                String retainedPullRequestUrl, Instant openedAt) {
    }

    private PipelineStateCodec() {
    }

    public static String write(PipelineState state) {
        return JSON.writeValueAsString(new Snapshot(FORMAT_VERSION, state.runId(), state.spec(),
                List.copyOf(state.selectedAgentIds()), withoutSecrets(state.request()),
                List.copyOf(state.completedStages()), List.copyOf(state.completedOrbs()),
                new LinkedHashMap<>(state.agentOutputs), gate(state.gate()), state.workflowScope()));
    }

    public static PipelineState read(String json) {
        Snapshot snapshot = JSON.readValue(json, Snapshot.class);
        if (snapshot.formatVersion() != FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported parked-run snapshot format " + snapshot.formatVersion());
        }
        var state = new PipelineState(snapshot.runId(), snapshot.spec(), Set.copyOf(snapshot.selectedAgentIds()),
                snapshot.request());
        snapshot.completedStages().forEach(state::markCompleted);
        snapshot.completedOrbs().forEach(state::markOrbCompleted);
        state.agentOutputs.putAll(snapshot.agentOutputs());
        state.setGate(gate(snapshot.gate()));
        state.setWorkflowScope(snapshot.workflowScope());
        return state;
    }

    private static AgentRunRequest withoutSecrets(AgentRunRequest r) {
        return new AgentRunRequest(r.customerId(), r.specFile(), r.agentIds(), r.requestedBy(), r.repositoryUrl(),
                r.gitUsername(), null, r.approvalGate(), r.baseBranch(), r.workflowId(), r.startedBy(), r.initiator(),
                r.chainAncestry());
    }

    private static Gate gate(ApprovalGateState g) {
        return g == null ? null : new Gate(g.placementStage(), g.feedbackSupported(), g.maxFeedbackIterations(),
                g.iteration(), g.isOpen(), g.isAcceptedFinal(), List.copyOf(g.decidedIterations()),
                List.copyOf(g.commentHistory()), g.pendingFeedback(), g.lastReport(), g.retainedBranch(),
                g.retainedPullRequestUrl(), g.openedAt());
    }

    private static ApprovalGateState gate(Gate g) {
        return g == null ? null : ApprovalGateState.restore(g.placementStage(), g.feedbackSupported(),
                g.maxFeedbackIterations(), g.iteration(), g.open(), g.acceptedFinal(), g.decidedIterations(),
                g.commentHistory(), g.pendingFeedback(), g.lastReport(), g.retainedBranch(),
                g.retainedPullRequestUrl(), g.openedAt());
    }
}
