package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.approval.model.ApprovalGateState;
import nl.metafactory.agents.domain.ImpactReport;
import nl.metafactory.agents.domain.ImplementationPlan;
import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.ReviewReport;
import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.domain.TestPlan;
import nl.metafactory.agents.model.AgentRunRequest;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Mutable per-run continuation state for {@link EmbabelOrchestrator#pipeline(PipelineState)}
 * (ADR-001, workflow-approval-gate architecture §4.3/§7.2, batch B4). Not persisted — a restart
 * loses any run parked mid-pipeline (BR-36), which is why an unknown run id maps to the explicit
 * terminal {@code RUN_STATE_LOST} status rather than an indefinite hang (V8).
 *
 * <p>Single-owner invariant (architecture §7.2, "the one honest cost"): at most one thread ever
 * executes {@code pipeline(state)} for a given state at a time. A parked run's registry entry
 * (added in batch B6) is removed under its {@code compute} lock, and its gate is flipped closed,
 * before a decision resubmits this same state object to {@code AsyncPipelineRunner} — so two
 * decisions can never race on the same mutable state (see {@code ApprovalDecisionService}).</p>
 */
public class PipelineState {

    private final String runId;
    private final SpecContent spec;
    private final Set<String> selectedAgentIds;
    private final AgentRunRequest request;
    private final Set<String> completedStages = new LinkedHashSet<>();
    private final Set<String> completedOrbs = new LinkedHashSet<>();

    // Package-private: mutated directly by EmbabelOrchestrator (same package) as each stage's
    // domain output becomes available, exactly like the local variables it replaces.
    RequirementAnalysis ra;
    ImpactReport ir;
    TestPlan tp;
    ImplementationPlan ip;
    ReviewReport rr;

    // Batch B6: null until the gate first opens (architecture §4.3). Public because
    // ApprovalGateCoordinator/ApprovalGateContextAssembler (a different package) both need it.
    private ApprovalGateState gate;

    public PipelineState(String runId, SpecContent spec, Set<String> selectedAgentIds, AgentRunRequest request) {
        this.runId = runId;
        this.spec = spec;
        this.selectedAgentIds = selectedAgentIds;
        this.request = request;
    }

    public String runId() {
        return runId;
    }

    public SpecContent spec() {
        return spec;
    }

    public AgentRunRequest request() {
        return request;
    }

    public boolean selected(String stageId) {
        return selectedAgentIds.contains(stageId);
    }

    /** True once this stage's block has already executed in a previous pass (post-resume skip). */
    public boolean isDone(String stageId) {
        return completedStages.contains(stageId);
    }

    /** Marks the stage done; returns true only the first time (idempotent), mirroring {@code Set.add}. */
    boolean markCompleted(String stageId) {
        return completedStages.add(stageId);
    }

    /**
     * Batch B8 loop-back seam: called by {@code ApprovalDecisionService} on
     * {@code ACCEPT_WITH_COMMENTS} so the resumed {@code pipeline()} pass re-enters the
     * realisation stage's block instead of skipping it as already done. Public because the
     * decision service lives in a different package.
     */
    public void reopenStage(String stageId) {
        completedStages.remove(stageId);
    }

    /**
     * True once the workflow orb identified by {@code key} has already been started in a
     * previous pass of {@code pipeline()}. Public because {@code nl.metafactory.agents.workflowtrigger}
     * is a different package, exactly mirroring why {@link #reopenStage} is public for the
     * {@code approval} package.
     */
    public boolean orbDone(String key) {
        return completedOrbs.contains(key);
    }

    /** Marks the workflow orb identified by {@code key} as started. */
    public void markOrbCompleted(String key) {
        completedOrbs.add(key);
    }

    public ApprovalGateState gate() {
        return gate;
    }

    public void setGate(ApprovalGateState gate) {
        this.gate = gate;
    }
}
