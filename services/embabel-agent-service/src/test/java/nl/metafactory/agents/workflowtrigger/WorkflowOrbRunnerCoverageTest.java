package nl.metafactory.agents.workflowtrigger;

import nl.metafactory.agents.config.WorkflowTriggerProperties;
import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.orchestration.AgentRunStore;
import nl.metafactory.agents.orchestration.PipelineState;
import nl.metafactory.agents.workflow.ChildStartDecision;
import nl.metafactory.agents.workflow.ChildWorkflowStarter;
import nl.metafactory.agents.workflow.WorkflowChainResolver;
import nl.metafactory.agents.workflow.WorkflowDefinitionRepository;
import nl.metafactory.agents.workflow.model.ExecutionConfig;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowOrb;
import nl.metafactory.agents.workflow.model.WorkflowOrbMode;
import nl.metafactory.agents.workflow.model.WorkflowStartResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowOrbRunnerCoverageTest {

    private WorkflowDefinitionRepository workflowDefinitionRepository;
    private WorkflowChainResolver workflowChainResolver;
    private WorkflowTriggerProperties workflowTriggerProperties;
    private ChildWaitRegistry childWaitRegistry;
    private DeadlineScheduler deadlineScheduler;
    private WorkflowTriggerCoordinator workflowTriggerCoordinator;
    private AgentRunStore agentRunStore;
    private ChildWorkflowStarter childWorkflowStarter;
    @SuppressWarnings("unchecked")
    private ObjectProvider<ChildWorkflowStarter> childWorkflowStarterProvider;
    private WorkflowOrbRunner runner;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        workflowDefinitionRepository = mock(WorkflowDefinitionRepository.class);
        workflowChainResolver = mock(WorkflowChainResolver.class);
        workflowTriggerProperties = new WorkflowTriggerProperties();
        childWaitRegistry = new ChildWaitRegistry();
        deadlineScheduler = mock(DeadlineScheduler.class);
        workflowTriggerCoordinator = mock(WorkflowTriggerCoordinator.class);
        agentRunStore = mock(AgentRunStore.class);
        childWorkflowStarter = mock(ChildWorkflowStarter.class);
        childWorkflowStarterProvider = mock(ObjectProvider.class);
        when(childWorkflowStarterProvider.getObject()).thenReturn(childWorkflowStarter);
        runner = new WorkflowOrbRunner(workflowDefinitionRepository, workflowChainResolver, workflowTriggerProperties,
                childWaitRegistry, deadlineScheduler, workflowTriggerCoordinator, agentRunStore, childWorkflowStarterProvider);
    }

    private PipelineState samplePipelineState(String runId, String requestedBy, List<String> chainAncestry) {
        var request = new AgentRunRequest("cust1", "spec.md", List.of("requirement"), requestedBy,
                "https://github.com/org/repo", null, null, null, null, null, null,
                RunInitiator.trigger("test"), chainAncestry);
        return new PipelineState(runId, new SpecContent(runId, "spec.md", "spec.md", ""),
                Set.of("requirement"), request);
    }

    private WorkflowDefinition parentWithOrbs(String id, List<WorkflowOrb> orbs) {
        return new WorkflowDefinition(id, "Parent-" + id, "proj", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null,
                new ExecutionConfig("cust1", "https://github.com/org/repo", 300),
                false, null, "ACTIVE", null, null, null, orbs);
    }

    private WorkflowDefinition target(String id) {
        return new WorkflowDefinition(id, "Target-" + id, "proj", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null,
                new ExecutionConfig("cust1", "https://github.com/org/repo", 300),
                false, null, "ACTIVE", null, null, null, null);
    }

    @Test
    void parallelOrbWithNullExecutionIdRecordsAnEventAndContinuesWithoutBlocking() {
        var orb = new WorkflowOrb("wf-child", WorkflowOrbMode.PARALLEL, null);
        var state = samplePipelineState("run-1", "workflow:wf-1", List.of("wf-1"));
        var parent = parentWithOrbs("wf-1", List.of(orb));
        var childTarget = target("wf-child");
        when(workflowDefinitionRepository.findById("wf-1")).thenReturn(Optional.of(parent));
        when(workflowChainResolver.resolve("wf-child", parent, List.of("wf-1")))
                .thenReturn(new ChildStartDecision.Permitted(childTarget));
        when(childWorkflowStarter.startWorkflowFromOrb(any()))
                .thenReturn(new WorkflowStartResponse("wf-child", null, "BLOCKED", Instant.now(), "denied by policy"));

        boolean result = runner.runOrbsAnchoredTo(state, null);

        assertThat(result).isFalse();
        verify(agentRunStore, never()).setStatus(any(), any());
    }

    @Test
    void theScheduledDeadlineTaskInvokesOnDeadlineExpiredForTheParent() {
        var orb = new WorkflowOrb("wf-child", WorkflowOrbMode.SEQUENTIAL, null);
        var state = samplePipelineState("run-1", "workflow:wf-1", List.of("wf-1"));
        var parent = parentWithOrbs("wf-1", List.of(orb));
        var childTarget = target("wf-child");
        when(workflowDefinitionRepository.findById("wf-1")).thenReturn(Optional.of(parent));
        when(workflowChainResolver.resolve("wf-child", parent, List.of("wf-1")))
                .thenReturn(new ChildStartDecision.Permitted(childTarget));
        when(childWorkflowStarter.startWorkflowFromOrb(any()))
                .thenReturn(new WorkflowStartResponse("wf-child", "run-child", "RUNNING", Instant.now(), "started"));
        ScheduledFuture<?> deadline = mock(ScheduledFuture.class);
        var taskCaptor = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        org.mockito.Mockito.doReturn(deadline).when(deadlineScheduler).schedule(taskCaptor.capture(), any());

        runner.runOrbsAnchoredTo(state, null);
        taskCaptor.getValue().run();

        verify(workflowTriggerCoordinator).onDeadlineExpired("run-1");
    }
}
