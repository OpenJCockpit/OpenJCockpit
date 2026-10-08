package nl.metafactory.agents.workflow;

import nl.metafactory.agents.model.AgentDefinition;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import nl.metafactory.agents.policy.PolicyDecisionService;
import nl.metafactory.agents.policy.model.PolicyDecision;
import nl.metafactory.agents.security.CurrentUserProvider;
import nl.metafactory.agents.workflow.model.ExecutionConfig;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowOrbMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AC-43: the existing "skill denial ⇒ block all" rule applies unchanged to a child started via
 * WorkflowExecutionService.startWorkflowFromOrb, not just to a direct dashboard/hermes/project-file
 * start.
 */
class WorkflowExecutionServiceOrbSkillDenialTest {

    private WorkflowDefinitionRepository workflowRepository;
    private AgentOrchestrator orchestrator;
    private PolicyDecisionService policyDecisionService;
    private CurrentUserProvider currentUserProvider;
    private WorkflowExecutionService service;

    private static PolicyDecision allow() {
        return new PolicyDecision(true, "allowed", false, "low", List.of(), null, null, null, Instant.now(), false, null);
    }

    private static PolicyDecision deny(String reason) {
        return new PolicyDecision(false, reason, false, "high", List.of(), null, null, null, Instant.now(), false, null);
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
                new AgentDefinition("requirement", "Requirement Agent", "d", "r", List.of(), List.of(), 0, "in", "out"),
                new AgentDefinition("evidence", "Evidence Agent", "d", "r", List.of(), List.of(), 1, "in", "out")
        ));
        when(policyDecisionService.canStartWorkflow(any())).thenReturn(allow());
        when(policyDecisionService.canUseAgent(any())).thenReturn(allow());
        when(policyDecisionService.canUseSubagent(any())).thenReturn(allow());
    }

    @Test
    void aChildStartedFromAnOrbWithADeniedSkillReturnsBlockedForAllAgents() {
        var child = new WorkflowDefinition("wf-child", "Child", "proj", null, "desc",
                List.of("requirement", "evidence"), List.of(), List.of("summarize"), List.of(), null,
                new ExecutionConfig("cust1", "https://github.com/org/repo", 300),
                false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-child")).thenReturn(Optional.of(child));
        when(policyDecisionService.canExecuteSkill(any())).thenReturn(deny("skill requires human approval"));

        var parentRequest = new AgentRunRequest("cust0", "parent-spec", List.of("requirement"), "workflow:wf-parent",
                "https://github.com/org/repo", null, null, null, null, null, null,
                RunInitiator.trigger("test"), List.of("wf-parent"));
        var command = new ChildStartCommand("wf-child", parentRequest, WorkflowOrbMode.SEQUENTIAL,
                RunInitiator.trigger("test"), List.of("wf-parent"));

        var response = service.startWorkflowFromOrb(command);

        assertThat(response.status()).isEqualTo("BLOCKED");
        assertThat(response.executionId()).isNull();
        assertThat(response.message()).isEqualTo("All pipeline agents of workflow 'wf-child' were denied by policy; nothing to run.");
        verify(orchestrator, never()).start(any());
        verify(workflowRepository, never()).save(any());
    }
}
