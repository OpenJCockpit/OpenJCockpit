package nl.metafactory.agents.approval;

import nl.metafactory.agents.approval.model.ApprovalGateState;
import nl.metafactory.agents.approval.model.StageChangeReport;
import nl.metafactory.agents.config.ApprovalGateProperties;
import nl.metafactory.agents.orchestration.AgentRunStore;
import nl.metafactory.agents.orchestration.PipelineState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The SINGLE gate-open seam (ADR-003, BR-40, AC-61): every stage's block funnels its
 * {@link StageChangeReport} through {@link #pauseAfter} and this is the only place that opens a
 * gate. {@code StageOutcome} never drives control flow here — it is carried through unchanged as
 * data on the parked {@link ApprovalGateState#lastReport()} for the context assembler to read.
 *
 * <p>{@code feedbackSupported} is computed in exactly ONE place — here, as
 * {@code "realisation".equals(placementStage)} — and nowhere else in the backend (BR-41, risk 8).
 */
@Component
public class ApprovalGateCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ApprovalGateCoordinator.class);
    private static final String REALISATION_STAGE = "realisation";

    private final ApprovalGateRegistry registry;
    private final AgentRunStore runStore;
    private final ApprovalGateProperties properties;

    public ApprovalGateCoordinator(ApprovalGateRegistry registry, AgentRunStore runStore,
                                    ApprovalGateProperties properties) {
        this.registry = registry;
        this.runStore = runStore;
        this.properties = properties;
    }

    /**
     * @return {@code true} if the run was parked (the caller must return from {@code pipeline()}
     *         without advancing further); {@code false} if this stage is not this workflow's gate,
     *         the gate is disabled, or the gate has already been accepted for good.
     */
    public boolean pauseAfter(PipelineState state, String stageId, StageChangeReport report) {
        var config = state.request().approvalGate();
        boolean isThisWorkflowsGate = config != null && config.enabled() && stageId.equals(config.placementStage());
        boolean alreadyAcceptedForGood = state.gate() != null && state.gate().isAcceptedFinal();
        if (!isThisWorkflowsGate || alreadyAcceptedForGood) {
            return false;
        }

        ApprovalGateState gate = state.gate();
        if (gate == null) {
            gate = new ApprovalGateState(config.placementStage(), REALISATION_STAGE.equals(config.placementStage()),
                    properties.getMaxFeedbackIterations());
            state.setGate(gate);
        }
        gate.incrementIteration();
        gate.retainBranchIfPresent(report.branch());
        gate.retainPullRequestUrlIfAbsent(report.pullRequestUrl());
        gate.setLastReport(report);
        gate.setOpen(true);
        gate.markOpenedNow();

        String runId = state.runId();
        runStore.setStatus(runId, "AWAITING_APPROVAL");
        runStore.recordEvent(runId, stageId,
                "Awaiting approval (iteration " + gate.iteration() + "): " + report.message(),
                "AWAITING_APPROVAL", report.branch() != null ? "git://" + report.branch() : "");

        log.info("approval.gate.opened runId={} placementStage={} iteration={} outcome={} feedbackSupported={} changedFileCount={}",
                runId, config.placementStage(), gate.iteration(), report.outcome(), gate.feedbackSupported(),
                report.changedFileCount());

        registry.park(runId, state);
        return true;
    }
}
