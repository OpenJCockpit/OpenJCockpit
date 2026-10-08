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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowOrbRunnerThreadBudgetTest {

    @Test
    void parkingSequentialParentsHoldsZeroPipelinePoolThreads() throws InterruptedException {
        WorkflowDefinitionRepository workflowDefinitionRepository = mock(WorkflowDefinitionRepository.class);
        WorkflowChainResolver workflowChainResolver = mock(WorkflowChainResolver.class);
        WorkflowTriggerProperties workflowTriggerProperties = new WorkflowTriggerProperties();
        ChildWaitRegistry childWaitRegistry = new ChildWaitRegistry();
        DeadlineScheduler deadlineScheduler = mock(DeadlineScheduler.class);
        ScheduledFuture<?> deadline = mock(ScheduledFuture.class);
        org.mockito.Mockito.doReturn(deadline).when(deadlineScheduler).schedule(any(), any());
        WorkflowTriggerCoordinator workflowTriggerCoordinator = mock(WorkflowTriggerCoordinator.class);
        AgentRunStore agentRunStore = mock(AgentRunStore.class);
        ChildWorkflowStarter childWorkflowStarter = mock(ChildWorkflowStarter.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChildWorkflowStarter> childWorkflowStarterProvider = mock(ObjectProvider.class);
        when(childWorkflowStarterProvider.getObject()).thenReturn(childWorkflowStarter);

        WorkflowOrbRunner runner = new WorkflowOrbRunner(workflowDefinitionRepository, workflowChainResolver,
                workflowTriggerProperties, childWaitRegistry, deadlineScheduler, workflowTriggerCoordinator,
                agentRunStore, childWorkflowStarterProvider);

        var orb = new WorkflowOrb("wf-child", WorkflowOrbMode.SEQUENTIAL, null);
        var childTarget = new WorkflowDefinition("wf-child", "Child", "proj", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null,
                new ExecutionConfig("cust1", "https://github.com/org/repo", 300),
                false, null, "ACTIVE", null, null, null, null);

        int poolSize = 2;
        int parentCount = 8;

        List<PipelineState> states = new ArrayList<>();
        for (int i = 0; i < parentCount; i++) {
            String runId = "parent-" + i;
            var parent = new WorkflowDefinition(runId, "Parent-" + runId, "proj", null, "desc",
                    List.of("requirement"), List.of(), List.of(), List.of(), null,
                    new ExecutionConfig("cust1", "https://github.com/org/repo", 300),
                    false, null, "ACTIVE", null, null, null, List.of(orb));
            when(workflowDefinitionRepository.findById(runId)).thenReturn(Optional.of(parent));
            when(workflowChainResolver.resolve("wf-child", parent, List.of(runId)))
                    .thenReturn(new ChildStartDecision.Permitted(childTarget));
            when(childWorkflowStarter.startWorkflowFromOrb(argThat(cmd -> cmd != null
                    && cmd.targetWorkflowId().equals("wf-child")
                    && cmd.parentRequest().requestedBy().equals("workflow:" + runId))))
                    .thenReturn(new WorkflowStartResponse("wf-child", "run-child-" + runId, "RUNNING", Instant.now(), "started"));

            var request = new AgentRunRequest("cust1", "spec.md", List.of("requirement"), "workflow:" + runId,
                    "https://github.com/org/repo", null, null, null, null, null, null,
                    RunInitiator.trigger("test"), List.of(runId));
            var state = new PipelineState(runId, new SpecContent(runId, "spec.md", "spec.md", ""),
                    Set.of("requirement"), request);
            states.add(state);
        }

        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        AtomicInteger completed = new AtomicInteger(0);

        for (PipelineState state : states) {
            pool.submit(() -> {
                boolean parked = runner.runOrbsAnchoredTo(state, null);
                if (parked) {
                    completed.incrementAndGet();
                }
            });
        }

        AtomicInteger unrelatedCompleted = new AtomicInteger(0);
        pool.submit(unrelatedCompleted::incrementAndGet);

        pool.shutdown();
        boolean finished = pool.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(completed.get()).isEqualTo(parentCount);
        assertThat(unrelatedCompleted.get()).isEqualTo(1);
        assertThat(childWaitRegistry.size()).isEqualTo(parentCount);
    }
}
