package nl.metafactory.agents.approval;

import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.model.AgentRunStatuses;
import nl.metafactory.agents.orchestration.AgentRunStore;
import nl.metafactory.agents.orchestration.PipelineState;
import nl.metafactory.agents.orchestration.PipelineStateCodec;
import nl.metafactory.agents.persistence.AgentRunReconciliationService;
import nl.metafactory.agents.workflowrun.WorkflowRunScopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * On startup, re-adopts every run parked at an approval gate ({@code agent_parked_runs}) so it can be
 * decided after a restart exactly as before it: the run becomes live in {@link AgentRunStore} again
 * (status {@code AWAITING_APPROVAL}), its {@link PipelineState} is cached in the
 * {@link ApprovalGateRegistry}, and its workflow run scope (workflow, run id, git workspace binding) is
 * registered again in {@link WorkflowRunScopes}, so the agents of the resumed run are attributed and
 * bound exactly as before. Runs as an {@link ApplicationRunner} after
 * {@link AgentRunReconciliationService}, i.e. before the application accepts traffic.
 *
 * <p>A row whose run already reached a terminal status is stale and is removed. A row whose snapshot
 * cannot be restored (corrupt, or written in an unsupported format) is never resumed from guesswork:
 * the run is failed with a persisted event and failure summary, and its row removed, so it neither
 * hangs at the gate nor resumes with misread state.</p>
 */
@Component
@Order(AgentRunReconciliationService.ORDER + 1)
public class ParkedRunRehydrator implements ApplicationRunner {

    static final String GATE_AGENT_ID = "approval-gate";
    static final String RESTORE_FAILED = "Approval gate state could not be restored after a restart; the run cannot be resumed";

    private static final Logger log = LoggerFactory.getLogger(ParkedRunRehydrator.class);

    private final ParkedRunRepository store;
    private final ApprovalGateRegistry registry;
    private final AgentRunStore runStore;
    private final WorkflowRunScopes runScopes;

    public ParkedRunRehydrator(ParkedRunRepository store, ApprovalGateRegistry registry, AgentRunStore runStore,
                               WorkflowRunScopes runScopes) {
        this.store = store;
        this.registry = registry;
        this.runStore = runStore;
        this.runScopes = runScopes;
    }

    @Override
    public void run(ApplicationArguments args) {
        int restored = 0;
        int failed = 0;
        int stale = 0;
        for (var row : store.findAll()) {
            // The foreign key (ON DELETE CASCADE) guarantees the run row exists.
            AgentRun run = runStore.find(row.runId()).orElseThrow();
            if (AgentRunStatuses.isTerminal(run.status()) || AgentRunStatuses.RUN_STATE_LOST.equals(run.status())) {
                store.claim(row.runId(), row.iteration(), row.version());
                stale++;
                continue;
            }
            runStore.adopt(run);
            PipelineState state;
            try {
                state = PipelineStateCodec.read(row.state());
                if (!row.runId().equals(state.runId())) {
                    throw new IllegalStateException("snapshot belongs to another run");
                }
            } catch (RuntimeException e) {
                failRun(row, e);
                failed++;
                continue;
            }
            runStore.setStatus(row.runId(), ParkedRunRepository.AWAITING_APPROVAL);
            registry.adopt(row, state);
            if (state.workflowScope() != null) {
                runScopes.register(state.workflowScope());
            }
            restored++;
        }
        log.info("approval.gate.rehydrated restored={} failed={} stale={}", restored, failed, stale);
    }

    private void failRun(ParkedRunRepository.ParkedRun row, RuntimeException cause) {
        log.warn("approval.gate.rehydration.failed runId={} reason={}", row.runId(), cause.getClass().getSimpleName());
        store.claim(row.runId(), row.iteration(), row.version());
        runStore.recordEvent(row.runId(), GATE_AGENT_ID, RESTORE_FAILED, "FAILED", "");
        runStore.setStatus(row.runId(), "FAILED", RESTORE_FAILED + " (" + cause.getClass().getSimpleName() + ")");
    }
}
