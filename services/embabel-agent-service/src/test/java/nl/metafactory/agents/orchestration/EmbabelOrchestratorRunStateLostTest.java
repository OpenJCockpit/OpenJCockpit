package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.approval.ApprovalGateCoordinator;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.persistence.InMemoryAgentRunPersistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * AC-42 server half (workflow-approval-gate, V8/BR-36/BR-37): a run id unknown to this instance —
 * whether never seen at all, or a paused run whose in-memory state was lost to a restart — must
 * map to a defined, terminal {@code RUN_STATE_LOST} status at HTTP 200, replacing the previous
 * {@code NOT_FOUND}-at-200 sentinel that made {@code WorkflowExecution.tsx} poll forever (its
 * {@code TERMINAL_STATUSES} set never contained {@code "NOT_FOUND"}). The HTTP-200-with-body half
 * of this contract is exercised in {@code AgentRunControllerTest}, which is a thin pass-through
 * over {@link AgentOrchestrator#get(String)} and therefore does not need its own special-casing —
 * this class is the proof that {@link EmbabelOrchestrator} itself produces the right value.
 */
class EmbabelOrchestratorRunStateLostTest {

    private EmbabelOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new EmbabelOrchestrator(mock(AsyncPipelineRunner.class),
                new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { }),
                mock(ApprovalGateCoordinator.class), mock(ApprovalGateRegistry.class),
                mock(nl.metafactory.agents.workflowtrigger.WorkflowOrbRunner.class),
                mock(nl.metafactory.agents.workflowtrigger.WorkflowTriggerCoordinator.class),
                mock(nl.metafactory.agents.workflow.agents.WorkflowAgentInvoker.class),
                new nl.metafactory.agents.workflow.agents.AgentWorkflowProperties(), new InMemoryAgentRunOutputs());
    }

    @Test
    void aRunIdNeverSeenByThisInstanceIsRunStateLost() {
        var run = orchestrator.get("never-seen-run-id");

        assertThat(run.status()).isEqualTo("RUN_STATE_LOST");
        assertThat(run.events()).isEmpty();
        assertThat(run.generatedArtifacts()).isEmpty();
        // R8 hazard guard: a run whose state is genuinely unknown must never carry a fabricated
        // failure summary — only FAILED runs may have one.
        assertThat(run.failureSummary()).isNull();
    }

    @Test
    void aDifferentUnknownRunIdIsAlsoRunStateLostNotAStaleCachedValue() {
        // Guards against a lazily-memoised sentinel: two different unknown ids must each
        // independently resolve to RUN_STATE_LOST, carrying their own runId.
        var first = orchestrator.get("run-a");
        var second = orchestrator.get("run-b");

        assertThat(first.runId()).isEqualTo("run-a");
        assertThat(second.runId()).isEqualTo("run-b");
        assertThat(first.status()).isEqualTo("RUN_STATE_LOST");
        assertThat(second.status()).isEqualTo("RUN_STATE_LOST");
    }
}
