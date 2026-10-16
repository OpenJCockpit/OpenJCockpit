package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.approval.ApprovalGateCoordinator;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.approval.model.ApprovalGateConfig;
import nl.metafactory.agents.approval.model.ApprovalGateState;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.persistence.InMemoryAgentRunPersistence;
import nl.metafactory.agents.workflow.agents.AgentInvocationException;
import nl.metafactory.agents.workflow.agents.AgentRef;
import nl.metafactory.agents.workflow.agents.AgentWorkflowProperties;
import nl.metafactory.agents.workflow.agents.WorkflowAgentInvoker;
import nl.metafactory.agents.workflowtrigger.WorkflowOrbRunner;
import nl.metafactory.agents.workflowtrigger.WorkflowTriggerCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Workflow runs execute the referenced catalog agents as a sequential chain (no built-in stages). */
class CatalogPipelineTest {

    static final List<String> CHAIN = List.of("a2a:litellm:business-analyst", "local:architect", "a2a:litellm:qa-engineer");

    WorkflowAgentInvoker invoker;
    ApprovalGateCoordinator gates;
    WorkflowOrbRunner orbs;
    AgentRunStore runStore;
    EmbabelOrchestrator orchestrator;
    InMemoryAgentRunOutputs outputs;

    @BeforeEach
    void setUp() {
        invoker = mock(WorkflowAgentInvoker.class);
        gates = mock(ApprovalGateCoordinator.class);
        orbs = mock(WorkflowOrbRunner.class);
        runStore = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
        outputs = new InMemoryAgentRunOutputs();
        var async = mock(AsyncPipelineRunner.class);
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(async).run(any());
        orchestrator = new EmbabelOrchestrator(async, runStore, gates, mock(ApprovalGateRegistry.class), orbs,
                mock(WorkflowTriggerCoordinator.class), invoker, new AgentWorkflowProperties(), outputs);
    }

    static AgentRunRequest request(List<String> agents, ApprovalGateConfig gate) {
        return new AgentRunRequest("acme", "Build a password reset", agents, "workflow:wf-1", "", null, null, gate,
                null, "wf-1", "alice", RunInitiator.trigger("test"), List.of("wf-1"));
    }

    @Test
    void runsReferencedAgentsInOrderPassingEarlierOutputsAlong() {
        when(invoker.invoke(eq(AgentRef.parse(CHAIN.get(0)).orElseThrow()), any(), any())).thenReturn("REQ-001 reset");
        when(invoker.invoke(eq(AgentRef.parse(CHAIN.get(1)).orElseThrow()), any(), any())).thenReturn("Design: token mail");
        when(invoker.invoke(eq(AgentRef.parse(CHAIN.get(2)).orElseThrow()), any(), any())).thenReturn("All AC verified");

        var run = orchestrator.start(request(CHAIN, null));

        var messages = ArgumentCaptor.forClass(String.class);
        var contexts = ArgumentCaptor.forClass(WorkflowAgentInvoker.Invocation.class);
        verify(invoker, times(3)).invoke(any(), messages.capture(), contexts.capture());
        assertThat(messages.getAllValues().get(0)).contains("# Workflow input", "Build a password reset")
                .doesNotContain("# Output of");
        assertThat(messages.getAllValues().get(1)).contains("# Output of a2a:litellm:business-analyst", "REQ-001 reset");
        assertThat(messages.getAllValues().get(2)).contains("REQ-001 reset", "Design: token mail");
        assertThat(contexts.getValue()).isEqualTo(new WorkflowAgentInvoker.Invocation(run.runId(), "acme", "alice",
                new nl.metafactory.agents.workflowrun.WorkflowRunScope("wf-1", run.runId(), null)));

        var stored = orchestrator.get(run.runId());
        assertThat(stored.status()).isEqualTo("COMPLETED");
        assertThat(stored.events()).filteredOn(e -> "OK".equals(e.status()))
                .extracting(e -> e.agentId()).containsExactlyElementsOf(CHAIN);
        assertThat(stored.events()).anyMatch(e -> e.title().equals("All AC verified"));
        assertThat(outputs.find(run.runId())).extracting(AgentRunOutputs.Output::agentId, AgentRunOutputs.Output::output)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(CHAIN.get(0), "REQ-001 reset"),
                        org.assertj.core.groups.Tuple.tuple(CHAIN.get(1), "Design: token mail"),
                        org.assertj.core.groups.Tuple.tuple(CHAIN.get(2), "All AC verified"));
    }

    @Test
    void aFailingAgentFailsTheRunAndStopsTheChain() {
        when(invoker.invoke(any(), any(), any())).thenReturn("ok")
                .thenThrow(new AgentInvocationException("A2A agent x failed: UNAVAILABLE"));

        var run = orchestrator.start(request(CHAIN, null));

        var stored = orchestrator.get(run.runId());
        assertThat(stored.status()).isEqualTo("FAILED");
        assertThat(stored.events()).anyMatch(e -> "FAILED".equals(e.status()) && e.title().contains("UNAVAILABLE"));
        verify(invoker, times(2)).invoke(any(), any(), any());
    }

    @Test
    void gateAnchoredToAnAgentParksTheRunAndFeedbackReRunsThatAgent() {
        when(invoker.invoke(any(), any(), any())).thenReturn("first draft", "second draft", "verified");
        String anchor = CHAIN.get(0);
        when(gates.pauseAfter(any(), eq(anchor), any())).thenAnswer(inv -> {
            PipelineState state = inv.getArgument(0);
            if (state.gate() == null) {
                state.setGate(new ApprovalGateState(anchor, true, 3));
                return true;
            }
            return false;
        });

        var run = orchestrator.start(request(CHAIN, new ApprovalGateConfig(true, anchor)));
        verify(invoker, times(1)).invoke(any(), any(), any());

        // reviewer: accept with comments -> the anchored agent re-runs with the feedback
        var states = ArgumentCaptor.forClass(PipelineState.class);
        verify(gates).pauseAfter(states.capture(), eq(anchor), any());
        PipelineState parked = states.getValue();
        parked.gate().setPendingFeedback("Add an expiry requirement");
        parked.reopenStage(anchor);
        orchestrator.resume(parked);

        var messages = ArgumentCaptor.forClass(String.class);
        verify(invoker, times(4)).invoke(any(), messages.capture(), any());
        assertThat(messages.getAllValues().get(1)).contains("# Reviewer feedback", "Add an expiry requirement",
                "# Your previous output", "first draft");
        assertThat(orchestrator.get(run.runId()).status()).isEqualTo("COMPLETED");
        // the feedback re-run replaces the anchored agent's stored output
        assertThat(outputs.find(run.runId()).getFirst().output()).isEqualTo("second draft");
    }

    @Test
    void orbsAnchorToAgentReferences() {
        when(invoker.invoke(any(), any(), any())).thenReturn("ok");
        orchestrator.start(request(CHAIN, null));
        verify(orbs).runOrbsAnchoredTo(any(), eq(null));
        for (String key : CHAIN) {
            verify(orbs).runOrbsAnchoredTo(any(), eq(key));
        }
    }

    @Test
    void messageStaysWithinBudgetKeepingTheNewestOutputs() {
        var props = new AgentWorkflowProperties();
        props.setMaxMessageChars(1_000);
        var o = new EmbabelOrchestrator(null, runStore, gates, null, orbs, null, invoker, props, new InMemoryAgentRunOutputs());
        var state = new PipelineState("r", new nl.metafactory.agents.domain.SpecContent("r", "spec.md", "x".repeat(5_000), ""),
                java.util.Set.of(), request(CHAIN, null));
        state.agentOutputs.put("a2a:litellm:business-analyst", "old ".repeat(200));
        state.agentOutputs.put("local:architect", "new ".repeat(50));

        String message = o.composeMessage(state, "a2a:litellm:qa-engineer", null);
        assertThat(message.length()).isLessThanOrEqualTo(1_000);
        assertThat(message).contains("# Output of local:architect").doesNotContain("# Output of a2a:litellm:business-analyst")
                .contains("[truncated]");
    }

    @Test
    void startRejectsMissingAgentsAndAnythingThatIsNotAnAgentReference() {
        for (List<String> agents : java.util.Arrays.asList(null, List.<String>of(), List.of("requirement"),
                List.of("local:x", "requirement"))) {
            assertThatThrownBy(() -> orchestrator.start(request(agents, null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(invoker, never()).invoke(any(), any(), any());
    }

    @Test
    void startCreatesTheRunWithTheRequestFields() {
        when(invoker.invoke(any(), any(), any())).thenReturn("ok");

        var run = orchestrator.start(request(CHAIN, null));

        assertThat(run.runId()).isNotBlank();
        assertThat(run.customerId()).isEqualTo("acme");
        assertThat(orchestrator.get(run.runId()).workflowId()).isEqualTo("wf-1");
    }

    @Test
    void aRunStoppedBeforeItsPipelineRunsEndsCancelledWithoutInvokingAgents() {
        Runnable[] captured = new Runnable[1];
        var async = mock(AsyncPipelineRunner.class);
        doAnswer(inv -> { captured[0] = inv.getArgument(0); return null; }).when(async).run(any());
        var o = new EmbabelOrchestrator(async, runStore, gates, mock(ApprovalGateRegistry.class), orbs,
                mock(WorkflowTriggerCoordinator.class), invoker, new AgentWorkflowProperties(), outputs);

        var run = o.start(request(CHAIN, null));
        o.stop(run.runId());
        captured[0].run();

        assertThat(o.get(run.runId()).status()).isEqualTo("CANCELLED");
        verify(invoker, never()).invoke(any(), any(), any());
    }
    @Test
    void aRunBoundToAGitWorkspacePassesItToEveryAgentAndReleasesItWhenTheRunEnds() {
        var scopes = new nl.metafactory.agents.workflowrun.WorkflowRunScopes();
        var workspace = new nl.metafactory.agents.gitworkspace.GitWorkspaceBinding("job-1", "https://github.com/org/repo.git", "openjcockpit/wf/abc");
        var async = mock(AsyncPipelineRunner.class);
        Runnable[] pipeline = new Runnable[1];
        doAnswer(inv -> { pipeline[0] = inv.getArgument(0); return null; }).when(async).run(any());
        var o = new EmbabelOrchestrator(async, runStore, gates, mock(ApprovalGateRegistry.class), orbs,
                mock(WorkflowTriggerCoordinator.class), invoker, new AgentWorkflowProperties(), outputs, scopes);
        when(invoker.invoke(any(), any(), any())).thenReturn("ok");

        var run = o.start(request(CHAIN, null), workspace);
        // registered before the pipeline runs (so even the first agent and A2A lookups see it), attributed to the run
        var scope = new nl.metafactory.agents.workflowrun.WorkflowRunScope("wf-1", run.runId(), workspace);
        assertThat(scopes.find(run.runId())).contains(scope);
        pipeline[0].run();

        var contexts = ArgumentCaptor.forClass(WorkflowAgentInvoker.Invocation.class);
        verify(invoker, times(3)).invoke(any(), any(), contexts.capture());
        assertThat(contexts.getAllValues()).allSatisfy(c -> assertThat(c.workflow()).isEqualTo(scope));
        assertThat(o.get(run.runId()).status()).isEqualTo("COMPLETED");
        assertThat(scopes.find(run.runId())).isEmpty();
    }

    @Test
    void aCheckpointFollowsEveryAgentBeforeTheNextOneStarts() {
        var checkpoints = mock(RunCheckpoints.class);
        when(checkpoints.afterAgent(any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(java.util.Optional.of("Checkpoint abc1234 pushed to b"));
        var async = mock(AsyncPipelineRunner.class);
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; }).when(async).run(any());
        var o = new EmbabelOrchestrator(async, runStore, gates,
                mock(ApprovalGateRegistry.class), orbs, mock(WorkflowTriggerCoordinator.class), invoker,
                new AgentWorkflowProperties(), outputs, new nl.metafactory.agents.workflowrun.WorkflowRunScopes(), checkpoints);
        when(invoker.invoke(any(), any(), any())).thenReturn("ok");

        var run = o.start(request(CHAIN, null));

        var order = inOrder(invoker, checkpoints);
        for (int i = 0; i < CHAIN.size(); i++) {
            order.verify(invoker).invoke(any(), any(), any());
            order.verify(checkpoints).afterAgent(any(), eq(CHAIN.get(i)), eq(i), eq(CHAIN.size()), eq("ok"));
        }
        assertThat(o.get(run.runId()).events()).anySatisfy(e -> assertThat(e.title()).contains("Checkpoint abc1234"));
    }

    /** The case from the OPA log: a dashboard run (no git workspace) still attributes its agents to the workflow. */
    @Test
    void everyRunHandsItsWorkflowAndRunIdToItsAgentsEvenWithoutGitWorkspace() {
        when(invoker.invoke(any(), any(), any())).thenReturn("ok");

        var run = orchestrator.start(request(CHAIN, null));

        var contexts = ArgumentCaptor.forClass(WorkflowAgentInvoker.Invocation.class);
        verify(invoker, times(3)).invoke(any(), any(), contexts.capture());
        assertThat(contexts.getAllValues()).allSatisfy(c -> {
            assertThat(c.workflow().workflowId()).isEqualTo("wf-1");
            assertThat(c.workflow().workflowRunId()).isEqualTo(run.runId());
            assertThat(c.workflow().gitWorkspace()).isNull();
        });
    }
}
