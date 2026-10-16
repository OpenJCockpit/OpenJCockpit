package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.approval.ApprovalGateCoordinator;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.approval.model.ApprovalGateConfig;
import nl.metafactory.agents.config.ApprovalGateProperties;
import nl.metafactory.agents.config.WorkflowTriggerProperties;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.workflow.ChildWorkflowStarter;
import nl.metafactory.agents.workflow.WorkflowChainResolver;
import nl.metafactory.agents.workflow.WorkflowDefinitionRepository;
import nl.metafactory.agents.workflow.model.ExecutionConfig;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowOrb;
import nl.metafactory.agents.workflow.model.WorkflowOrbMode;
import nl.metafactory.agents.workflowtrigger.ChildWaitRegistry;
import nl.metafactory.agents.workflowtrigger.DeadlineScheduler;
import nl.metafactory.agents.workflowtrigger.WorkflowOrbRunner;
import nl.metafactory.agents.workflowtrigger.WorkflowTriggerCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Invariant I-4 (workflow-trigger-workflow-orb architecture): a run must never be simultaneously
 * present in both {@link nl.metafactory.agents.approval.ApprovalGateRegistry} and
 * {@link ChildWaitRegistry}. This wires the real {@link EmbabelOrchestrator},
 * {@link ApprovalGateCoordinator}, {@link ApprovalGateRegistry}, {@link WorkflowOrbRunner} and
 * {@link ChildWaitRegistry} together and constructs a workflow whose approval gate and whose
 * workflow orb are BOTH anchored to the same stage ("local:impact"), to prove only one registry ever
 * ends up holding the parked state for a given run.
 */
class EmbabelOrchestratorGateOrbMutualExclusionTest {

    private ApprovalGateRegistry gateRegistry;
    private ChildWaitRegistry childWaitRegistry;
    private EmbabelOrchestrator orchestrator;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        var invoker = mock(nl.metafactory.agents.workflow.agents.WorkflowAgentInvoker.class);
        when(invoker.invoke(any(), any(), any())).thenReturn("impact report");

        var runStore = new AgentRunStore(new nl.metafactory.agents.persistence.InMemoryAgentRunPersistence(), event -> { });
        gateRegistry = new ApprovalGateRegistry();
        var gateProperties = new ApprovalGateProperties();
        var gateCoordinator = new ApprovalGateCoordinator(gateRegistry, runStore, gateProperties);

        childWaitRegistry = new ChildWaitRegistry();
        var workflowDefinitionRepository = mock(WorkflowDefinitionRepository.class);
        var workflowChainResolver = mock(WorkflowChainResolver.class);
        var workflowTriggerProperties = new WorkflowTriggerProperties();
        var deadlineScheduler = mock(DeadlineScheduler.class);
        var workflowTriggerCoordinator = mock(WorkflowTriggerCoordinator.class);
        var childWorkflowStarter = mock(ChildWorkflowStarter.class);
        ObjectProvider<ChildWorkflowStarter> childWorkflowStarterProvider = mock(ObjectProvider.class);
        when(childWorkflowStarterProvider.getObject()).thenReturn(childWorkflowStarter);

        var orb = new WorkflowOrb("wf-child", WorkflowOrbMode.SEQUENTIAL, "local:impact");
        var parentWorkflow = new WorkflowDefinition("wf-1", "Parent", "proj", null, "desc",
                List.of("local:impact", "local:test-design", "a2a:litellm:review"),
                List.of(), List.of(), List.of(), null,
                new ExecutionConfig("cust1", "https://github.com/org/repo", 300),
                false, null, "ACTIVE", null, null,
                new ApprovalGateConfig(true, "local:impact"), List.of(orb));
        when(workflowDefinitionRepository.findById("wf-1")).thenReturn(Optional.of(parentWorkflow));

        var workflowOrbRunner = new WorkflowOrbRunner(workflowDefinitionRepository, workflowChainResolver,
                workflowTriggerProperties, childWaitRegistry, deadlineScheduler, workflowTriggerCoordinator,
                runStore, childWorkflowStarterProvider);

        orchestrator = new EmbabelOrchestrator(new AsyncPipelineRunner(), runStore, gateCoordinator, gateRegistry,
                workflowOrbRunner, workflowTriggerCoordinator, invoker,
                new nl.metafactory.agents.workflow.agents.AgentWorkflowProperties(), new InMemoryAgentRunOutputs());
    }

    @Test
    void aRunAnchoredToTheSameStageForBothAGateAndAnOrbIsNeverParkedInBothRegistriesAtOnce() {
        var request = new AgentRunRequest("cust1", "file.md",
                List.of("local:impact", "local:test-design", "a2a:litellm:review"),
                "workflow:wf-1", "https://github.com/org/repo", null, null,
                new ApprovalGateConfig(true, "local:impact"), null, null, null,
                RunInitiator.trigger("test"), List.of("wf-1"));

        var run = orchestrator.start(request);

        boolean parkedInGateRegistry = gateRegistry.isParked(run.runId());
        boolean parkedInChildWaitRegistry = childWaitRegistry.size() > 0;
        assertThat(parkedInGateRegistry && parkedInChildWaitRegistry).isFalse();
        assertThat(parkedInGateRegistry).isTrue();
        assertThat(childWaitRegistry.size()).isZero();
    }
}
