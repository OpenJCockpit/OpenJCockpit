package nl.metafactory.agents.workflow;

import nl.metafactory.agents.model.AgentDefinition;
import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import nl.metafactory.agents.policy.PolicyDecisionService;
import nl.metafactory.agents.policy.model.PolicyDecision;
import nl.metafactory.agents.security.CurrentUserProvider;
import nl.metafactory.agents.workflow.model.ExecutionConfig;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowOrb;
import nl.metafactory.agents.workflow.model.WorkflowOrbMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowExecutionServiceOrbTest {

    private WorkflowDefinitionRepository workflowRepository;
    private AgentSpecRepository agentSpecRepository;
    private SubagentSpecRepository subagentSpecRepository;
    private SkillSpecRepository skillSpecRepository;
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
        agentSpecRepository = mock(AgentSpecRepository.class);
        subagentSpecRepository = mock(SubagentSpecRepository.class);
        skillSpecRepository = mock(SkillSpecRepository.class);
        orchestrator = mock(AgentOrchestrator.class);
        policyDecisionService = mock(PolicyDecisionService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("test-user"));
        service = new WorkflowExecutionService(workflowRepository, agentSpecRepository,
                subagentSpecRepository, skillSpecRepository, orchestrator, policyDecisionService,
                currentUserProvider);

        when(orchestrator.availableAgents()).thenReturn(List.of(
                new AgentDefinition("requirement", "Requirement Agent", "d", "r", List.of(), List.of(), 0, "in", "out"),
                new AgentDefinition("impact", "Impact Agent", "d", "r", List.of(), List.of(), 1, "in", "out")
        ));
        when(policyDecisionService.canStartWorkflow(any())).thenReturn(allow());
        when(policyDecisionService.canUseAgent(any())).thenReturn(allow());
        when(policyDecisionService.canUseSubagent(any())).thenReturn(allow());
        when(policyDecisionService.canExecuteSkill(any())).thenReturn(allow());
    }

    private WorkflowDefinition workflowWithOrbs(String id, List<String> agentIds, List<WorkflowOrb> orbs) {
        return new WorkflowDefinition(id, "Name-" + id, "proj", null, "desc",
                agentIds, List.of(), List.of(), List.of(), null,
                new ExecutionConfig("cust1", "https://github.com/org/repo", 300),
                false, null, "ACTIVE", null, null, null, orbs);
    }

    @Test
    void startWorkflowFromOrbAppliesPolicyWithWorkflowTriggerSourceAndStartsTheChild() {
        var child = workflowWithOrbs("wf-child", List.of("requirement"), null);
        when(workflowRepository.findById("wf-child")).thenReturn(Optional.of(child));
        var run = new AgentRun("run-child", "cust1", "desc", "https://github.com/org/repo",
                "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null);
        when(orchestrator.start(any())).thenReturn(run);

        var parentRequest = new AgentRunRequest("cust0", "parent-spec", List.of("requirement"), "workflow:wf-parent",
                "https://github.com/org/repo", "user", "token", null, "main", null, null,
                RunInitiator.trigger("test"), List.of("wf-parent"));
        var command = new ChildStartCommand("wf-child", parentRequest, WorkflowOrbMode.SEQUENTIAL,
                RunInitiator.trigger("test"), List.of("wf-parent"));

        var response = service.startWorkflowFromOrb(command);

        assertThat(response.executionId()).isEqualTo("run-child");
        var captor = ArgumentCaptor.forClass(AgentRunRequest.class);
        org.mockito.Mockito.verify(orchestrator).start(captor.capture());
        assertThat(captor.getValue().specFile()).isEqualTo("parent-spec");
        assertThat(captor.getValue().repositoryUrl()).isEqualTo("https://github.com/org/repo");
        assertThat(captor.getValue().gitUsername()).isEqualTo("user");
        assertThat(captor.getValue().gitToken()).isEqualTo("token");
        assertThat(captor.getValue().baseBranch()).isEqualTo("main");
        assertThat(captor.getValue().chainAncestry()).containsExactly("wf-parent", "wf-child");
    }

    @Test
    void startWorkflowFromOrbReturnsBlockedWhenPolicyDenies() {
        var child = workflowWithOrbs("wf-child", List.of("requirement"), null);
        when(workflowRepository.findById("wf-child")).thenReturn(Optional.of(child));
        when(policyDecisionService.canStartWorkflow(any())).thenReturn(deny("denied by policy"));

        var parentRequest = new AgentRunRequest("cust0", "parent-spec", List.of("requirement"), "workflow:wf-parent",
                "https://github.com/org/repo", null, null, null, null, null, null,
                RunInitiator.trigger("test"), List.of("wf-parent"));
        var command = new ChildStartCommand("wf-child", parentRequest, WorkflowOrbMode.SEQUENTIAL,
                RunInitiator.trigger("test"), List.of("wf-parent"));

        var response = service.startWorkflowFromOrb(command);

        assertThat(response.executionId()).isNull();
        assertThat(response.status()).isEqualTo("BLOCKED");
    }

    @Test
    void startWorkflowFromOrbThrowsWhenInitiatorIsNull() {
        var parentRequest = new AgentRunRequest("cust0", "parent-spec", List.of("requirement"), "workflow:wf-parent",
                "https://github.com/org/repo", null, null, null, null, null, null,
                RunInitiator.trigger("test"), List.of("wf-parent"));
        var command = new ChildStartCommand("wf-child", parentRequest, WorkflowOrbMode.SEQUENTIAL, null, List.of("wf-parent"));

        assertThatThrownBy(() -> service.startWorkflowFromOrb(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without a resolved initiator");
    }

    @Test
    void startWorkflowReturnsBlockedWhenAnOrbAnchorIsNoLongerAmongTheAgentIds() {
        var orb = new WorkflowOrb("wf-other", WorkflowOrbMode.SEQUENTIAL, "not-selected");
        var workflow = workflowWithOrbs("wf-1", List.of("requirement"), List.of(orb));
        when(workflowRepository.findById("wf-1")).thenReturn(Optional.of(workflow));

        var response = service.startWorkflow("wf-1", nl.metafactory.agents.workflow.model.WorkflowStartInput.empty(), RunInitiator.trigger("test"));

        assertThat(response.executionId()).isNull();
        assertThat(response.status()).isEqualTo("BLOCKED");
        assertThat(response.message()).contains("not-selected");
    }

    @Test
    void startWorkflowSucceedsWhenAllOrbAnchorsAreStillAmongTheAgentIds() {
        var orb = new WorkflowOrb("wf-other", WorkflowOrbMode.SEQUENTIAL, "requirement");
        var workflow = workflowWithOrbs("wf-1", List.of("requirement"), List.of(orb));
        when(workflowRepository.findById("wf-1")).thenReturn(Optional.of(workflow));
        var run = new AgentRun("run-1", "cust1", "desc", "https://github.com/org/repo",
                "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null);
        when(orchestrator.start(any())).thenReturn(run);

        var response = service.startWorkflow("wf-1", nl.metafactory.agents.workflow.model.WorkflowStartInput.empty(), RunInitiator.trigger("test"));

        assertThat(response.executionId()).isEqualTo("run-1");
    }
}
