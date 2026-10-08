package nl.metafactory.agents.workflow;

import nl.metafactory.agents.model.AgentDefinition;
import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import nl.metafactory.agents.policy.PolicyDecisionService;
import nl.metafactory.agents.policy.model.PolicyDecision;
import nl.metafactory.agents.security.CurrentUserProvider;
import nl.metafactory.agents.policy.model.PolicyDecisionContext;
import nl.metafactory.agents.workflow.model.ExecutionConfig;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowOrbMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AC-44 at depth 2: workflow A (started by a top-level human) triggers B, which triggers C. Proves
 * the ORIGINAL top-level human RunInitiator (ricky's username+subject) is threaded through, byte-
 * for-byte, into both B's and C's AgentRunRequest and PolicyDecisionContext — never re-derived,
 * never replaced by B's own identity (B has none of its own to re-derive; a trigger initiator is
 * carried explicitly end to end).
 */
class WorkflowExecutionServiceOrbInitiatorPropagationTest {

    private WorkflowDefinitionRepository workflowRepository;
    private AgentOrchestrator orchestrator;
    private PolicyDecisionService policyDecisionService;
    private CurrentUserProvider currentUserProvider;
    private WorkflowExecutionService service;

    private static PolicyDecision allow() {
        return new PolicyDecision(true, "allowed", false, "low", List.of(), null, null, null, Instant.now(), false, null);
    }

    private WorkflowDefinition child(String id) {
        return new WorkflowDefinition(id, "Name-" + id, "proj", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null,
                new ExecutionConfig("cust1", "https://github.com/org/repo", 300),
                false, null, "ACTIVE", null, null, null, null);
    }

    @BeforeEach
    void setUp() {
        workflowRepository = mock(WorkflowDefinitionRepository.class);
        var agentSpecRepository = mock(AgentSpecRepository.class);
        var subagentSpecRepository = mock(SubagentSpecRepository.class);
        var skillSpecRepository = mock(SkillSpecRepository.class);
        orchestrator = mock(AgentOrchestrator.class);
        policyDecisionService = mock(PolicyDecisionService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("test-user"));
        service = new WorkflowExecutionService(workflowRepository, agentSpecRepository,
                subagentSpecRepository, skillSpecRepository, orchestrator, policyDecisionService,
                currentUserProvider);

        when(orchestrator.availableAgents()).thenReturn(List.of(
                new AgentDefinition("requirement", "Requirement Agent", "d", "r", List.of(), List.of(), 0, "in", "out")
        ));
        when(policyDecisionService.canStartWorkflow(any())).thenReturn(allow());
        when(policyDecisionService.canUseAgent(any())).thenReturn(allow());
        when(policyDecisionService.canUseSubagent(any())).thenReturn(allow());
        when(policyDecisionService.canExecuteSkill(any())).thenReturn(allow());
    }

    @Test
    void theTopLevelHumanInitiatorSurvivesUnchangedThroughTwoLevelsOfChaining() {
        var topLevelHuman = RunInitiator.human("ricky", "sub-ricky-123");

        when(workflowRepository.findById("wf-b")).thenReturn(Optional.of(child("wf-b")));
        var runB = new AgentRun("run-b", "cust1", "desc", "https://github.com/org/repo",
                "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null);
        when(orchestrator.start(any())).thenReturn(runB);

        var parentRequestA = new AgentRunRequest("cust0", "a-spec", List.of("requirement"), "workflow:wf-a",
                "https://github.com/org/repo", null, null, null, null, null, null, topLevelHuman, List.of("wf-a"));
        var commandAtoB = new ChildStartCommand("wf-b", parentRequestA, WorkflowOrbMode.SEQUENTIAL,
                topLevelHuman, List.of("wf-a"));

        service.startWorkflowFromOrb(commandAtoB);

        var contextCaptorB = ArgumentCaptor.forClass(PolicyDecisionContext.class);
        var runRequestCaptorB = ArgumentCaptor.forClass(AgentRunRequest.class);
        org.mockito.Mockito.verify(policyDecisionService).canStartWorkflow(contextCaptorB.capture());
        org.mockito.Mockito.verify(orchestrator).start(runRequestCaptorB.capture());
        assertThat(runRequestCaptorB.getValue().initiator()).isSameAs(topLevelHuman);
        assertThat(runRequestCaptorB.getValue().initiator().username()).isEqualTo("ricky");
        assertThat(runRequestCaptorB.getValue().initiator().subject()).isEqualTo("sub-ricky-123");
        assertThat(contextCaptorB.getValue().userId()).isEqualTo("ricky");
        var parentRequestB = runRequestCaptorB.getValue();

        when(workflowRepository.findById("wf-c")).thenReturn(Optional.of(child("wf-c")));
        var runC = new AgentRun("run-c", "cust1", "desc", "https://github.com/org/repo",
                "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null);
        when(orchestrator.start(any())).thenReturn(runC);

        var commandBtoC = new ChildStartCommand("wf-c", parentRequestB, WorkflowOrbMode.SEQUENTIAL,
                parentRequestB.initiator(), parentRequestB.chainAncestry());

        service.startWorkflowFromOrb(commandBtoC);

        var runRequestCaptorC = ArgumentCaptor.forClass(AgentRunRequest.class);
        org.mockito.Mockito.verify(orchestrator, org.mockito.Mockito.times(2)).start(runRequestCaptorC.capture());
        var runRequestC = runRequestCaptorC.getAllValues().get(1);
        assertThat(runRequestC.initiator()).isSameAs(topLevelHuman);
        assertThat(runRequestC.initiator().username()).isEqualTo("ricky");
        assertThat(runRequestC.initiator().subject()).isEqualTo("sub-ricky-123");
        assertThat(runRequestC.chainAncestry()).containsExactly("wf-a", "wf-b", "wf-c");
    }
}
