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

class WorkflowExecutionServiceOrbSecurityTest {

    private WorkflowDefinitionRepository workflowRepository;
    private AgentOrchestrator orchestrator;
    private PolicyDecisionService policyDecisionService;
    private CurrentUserProvider currentUserProvider;
    private WorkflowExecutionService service;

    private static PolicyDecision allow() {
        return new PolicyDecision(true, "allowed", false, "low", List.of(), null, null, null, Instant.now(), false, null);
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

    private WorkflowDefinition child(String id) {
        return new WorkflowDefinition(id, "Name-" + id, "proj", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null,
                new ExecutionConfig("cust1", "https://github.com/org/repo", 300),
                false, null, "ACTIVE", null, null, null, null);
    }

    @Test
    void startWorkflowFromOrbAlwaysGoesThroughPolicyWithTheDistinctWorkflowTriggerSource() {
        when(workflowRepository.findById("wf-child")).thenReturn(Optional.of(child("wf-child")));
        var run = new AgentRun("run-child", "cust1", "desc", "https://github.com/org/repo",
                "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null);
        when(orchestrator.start(any())).thenReturn(run);

        var parentRequest = new AgentRunRequest("cust0", "parent-spec", List.of("requirement"), "workflow:wf-parent",
                "https://github.com/org/repo", "secret-user", "secret-token-value", null, "main", null, null,
                RunInitiator.trigger("test"), List.of("wf-parent"));
        var command = new ChildStartCommand("wf-child", parentRequest, WorkflowOrbMode.SEQUENTIAL,
                RunInitiator.trigger("test"), List.of("wf-parent"));

        service.startWorkflowFromOrb(command);

        var contextCaptor = ArgumentCaptor.forClass(PolicyDecisionContext.class);
        org.mockito.Mockito.verify(policyDecisionService).canStartWorkflow(contextCaptor.capture());
        assertThat(contextCaptor.getValue().triggerSource()).isEqualTo("workflow-trigger");
    }

    @Test
    void startWorkflowFromOrbResponseMessageNeverContainsTheGitToken() {
        when(workflowRepository.findById("wf-child")).thenReturn(Optional.of(child("wf-child")));
        var run = new AgentRun("run-child", "cust1", "desc", "https://github.com/org/repo",
                "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null);
        when(orchestrator.start(any())).thenReturn(run);

        var parentRequest = new AgentRunRequest("cust0", "parent-spec", List.of("requirement"), "workflow:wf-parent",
                "https://github.com/org/repo", "secret-user", "super-secret-token-xyz", null, "main", null, null,
                RunInitiator.trigger("test"), List.of("wf-parent"));
        var command = new ChildStartCommand("wf-child", parentRequest, WorkflowOrbMode.SEQUENTIAL,
                RunInitiator.trigger("test"), List.of("wf-parent"));

        var response = service.startWorkflowFromOrb(command);

        assertThat(response.message()).doesNotContain("super-secret-token-xyz");
    }
}
