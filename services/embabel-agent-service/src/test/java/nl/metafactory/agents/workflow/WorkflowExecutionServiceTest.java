package nl.metafactory.agents.workflow;

import nl.metafactory.agents.approval.model.ApprovalGateConfig;
import nl.metafactory.agents.model.AgentDefinition;
import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import nl.metafactory.agents.policy.PolicyDecisionService;
import nl.metafactory.agents.policy.model.PolicyDecision;
import nl.metafactory.agents.security.CurrentUserProvider;
import nl.metafactory.agents.workflow.model.ExecutionConfig;
import nl.metafactory.agents.workflow.model.SkillSpec;
import nl.metafactory.agents.workflow.model.SubagentSpec;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowExecutionServiceTest {

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

    private static String parseWorkflowIdFromRequestedBy(String requestedBy) throws Exception {
        var method = Class.forName("nl.metafactory.agents.spec.SpecGitPublisher")
                .getDeclaredMethod("workflowId", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, requestedBy);
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
                new AgentDefinition("evidence", "Evidence Agent", "d", "r", List.of(), List.of(), 5, "in", "out")
        ));

        // Default: everything allowed, matching "OPA disabled/not configured" behaviour.
        when(policyDecisionService.canStartWorkflow(any())).thenReturn(allow());
        when(policyDecisionService.canAcceptHermesSignal(any())).thenReturn(allow());
        when(policyDecisionService.canProcessProjectFile(any())).thenReturn(allow());
        when(policyDecisionService.canUseAgent(any())).thenReturn(allow());
        when(policyDecisionService.canUseSubagent(any())).thenReturn(allow());
        when(policyDecisionService.canExecuteSkill(any())).thenReturn(allow());
    }

    @Test
    void throwsNotFoundWhenWorkflowDoesNotExist() {
        when(workflowRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startWorkflow("missing", nl.metafactory.agents.workflow.model.WorkflowStartInput.empty(), RunInitiator.trigger("test")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void startsRunWithOnlyAgentIdsThatExistInTheCatalogue() throws Exception {
        var workflow = new WorkflowDefinition("wf-1", "Onboarding", "Noordzee Logistics", null, "desc",
                List.of("requirement", "evidence", "not-a-real-agent"), List.of("log-collector"), List.of("summarize"),
                List.of(), null, new ExecutionConfig("cust1", "https://github.com/org/repo", 300),
                false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-1")).thenReturn(Optional.of(workflow));
        when(subagentSpecRepository.findByName("log-collector")).thenReturn(Optional.of(
                new SubagentSpec("log-collector", "requirement", "d", "r", "i", List.of(), List.of(), "wf-1")));
        when(skillSpecRepository.findByName("summarize")).thenReturn(Optional.of(
                new SkillSpec("summarize", "d", "in", "out", "i", List.of(), null)));

        var run = new AgentRun("run-1", "cust1", "desc", "https://github.com/org/repo",
                "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null);
        when(orchestrator.start(any())).thenReturn(run);

        var response = service.startWorkflow("wf-1", nl.metafactory.agents.workflow.model.WorkflowStartInput.empty(), RunInitiator.trigger("test"));

        assertThat(response.workflowId()).isEqualTo("wf-1");
        assertThat(response.executionId()).isEqualTo("run-1");
        assertThat(response.status()).isEqualTo("RUNNING");
        assertThat(response.message()).contains("requirement").contains("evidence").doesNotContain("not-a-real-agent");

        var captor = ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(orchestrator).start(captor.capture());
        assertThat(captor.getValue().agentIds()).containsExactly("requirement", "evidence");
        assertThat(captor.getValue().customerId()).isEqualTo("cust1");
        assertThat(captor.getValue().repositoryUrl()).isEqualTo("https://github.com/org/repo");
        assertThat(captor.getValue().workflowId()).isEqualTo("wf-1");
        // F-13 drift guard: the typed workflowId field and the legacy string-encoded requestedBy
        // must agree on the same workflow id for a real request built by the real service method.
        assertThat(parseWorkflowIdFromRequestedBy(captor.getValue().requestedBy())).isEqualTo("wf-1");
        assertThat(captor.getValue().startedBy()).isEqualTo("test-user");

        // workflow-execution-state-to-database / S1+BR-2: the post-start definition re-save is
        // removed; the workflow definition file is configuration-only. This assertion is
        // INVERTED from "the summary pair is written to YAML" to "nothing is written to YAML",
        // which is a strictly stronger assertion on this path, not a weakened one. The summary
        // pair's new source of truth is agent_runs, covered by WorkflowLastExecutionServiceTest
        // and AC-02.
        verify(workflowRepository, never()).save(any());
        assertThat(captor.getValue().chainAncestry()).containsExactly("wf-1");
    }

    @Test
    void startWorkflowChainAncestryIsASingleElementListOfItsOwnWorkflowIdForATopLevelRun() {
        var workflow = new WorkflowDefinition("wf-ancestry", "Name", "Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-ancestry")).thenReturn(Optional.of(workflow));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-ancestry", "Project", "desc", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));

        service.startWorkflow("wf-ancestry", nl.metafactory.agents.workflow.model.WorkflowStartInput.empty(), RunInitiator.trigger("test"));

        var captor = ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(orchestrator).start(captor.capture());
        assertThat(captor.getValue().chainAncestry()).containsExactly("wf-ancestry");
    }

    @Test
    void identityIsCarriedExplicitlyThroughEvenWithAnEmptySecurityContext() {
        SecurityContextHolder.clearContext();
        var workflow = new WorkflowDefinition("wf-identity", "Name", "Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-identity")).thenReturn(Optional.of(workflow));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-identity", "Project", "desc", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));

        service.startWorkflow("wf-identity", nl.metafactory.agents.workflow.model.WorkflowStartInput.empty(),
                RunInitiator.human("alice", "sub-alice"));

        var captor = ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(orchestrator).start(captor.capture());
        assertThat(captor.getValue().initiator()).isEqualTo(RunInitiator.human("alice", "sub-alice"));
        assertThat(captor.getValue().initiator().kind()).isEqualTo(RunInitiator.RunInitiatorKind.HUMAN);
    }

    @Test
    void nullInitiatorFailsLoudlyInsteadOfSilentlyDefaulting() {
        var workflow = new WorkflowDefinition("wf-null-initiator", "Name", "Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-null-initiator")).thenReturn(Optional.of(workflow));

        assertThatThrownBy(() -> service.startWorkflow("wf-null-initiator",
                nl.metafactory.agents.workflow.model.WorkflowStartInput.empty(), null))
                .isInstanceOf(NullPointerException.class);

        verify(orchestrator, never()).start(any());
    }

    @Test
    void agentIdsFilteringExcludesAnyIdNotPresentInAvailableAgents() {
        // BR-2 regression guard: doStart's existing availableAgentIds::contains intersection
        // must keep excluding any id (whether a stray agent id or a smuggled workflow id) that
        // is not present in orchestrator.availableAgents(), independent of this feature's new
        // orb-reference rules.
        var workflow = new WorkflowDefinition("wf-br2", "Name", "Project", null, "desc",
                List.of("requirement", "smuggled-workflow-id-not-a-real-agent"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-br2")).thenReturn(Optional.of(workflow));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-br2", "Project", "desc", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));

        service.startWorkflow("wf-br2", nl.metafactory.agents.workflow.model.WorkflowStartInput.empty(), RunInitiator.trigger("test"));

        var captor = ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(orchestrator).start(captor.capture());
        assertThat(captor.getValue().agentIds()).containsExactly("requirement");
    }

    @Test
    void startWithPromptUsesPromptAsRunPayload() {
        var workflow = new WorkflowDefinition("wf-p", "Name", "Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, true, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-p")).thenReturn(Optional.of(workflow));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-p", "Project", "prompt", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));

        service.startWorkflow("wf-p", new nl.metafactory.agents.workflow.model.WorkflowStartInput(
                "Create a small spec for the login page", null, null, null, null, null), nl.metafactory.agents.model.RunInitiator.trigger("test"));

        var captor = org.mockito.ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(orchestrator).start(captor.capture());
        assertThat(captor.getValue().specFile()).isEqualTo("Create a small spec for the login page");
    }

    @Test
    void startWithPromptWinsOverAmbientSpecFileSelection() {
        var workflow = new WorkflowDefinition("wf-s", "Name", "Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, true, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-s")).thenReturn(Optional.of(workflow));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-s", "Project", "spec", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));

        service.startWorkflow("wf-s", new nl.metafactory.agents.workflow.model.WorkflowStartInput(
                "Create a spec for login", "001-example-feature/spec.md", null, null, null, null), nl.metafactory.agents.model.RunInitiator.trigger("test"));

        var captor = org.mockito.ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(orchestrator).start(captor.capture());
        assertThat(captor.getValue().specFile()).isEqualTo("Create a spec for login");
    }

    @Test
    void startWithSpecFileUsesItWhenNoPromptGiven() {
        var workflow = new WorkflowDefinition("wf-f", "Name", "Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-f")).thenReturn(Optional.of(workflow));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-f", "Project", "spec", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));

        service.startWorkflow("wf-f", new nl.metafactory.agents.workflow.model.WorkflowStartInput(
                null, "001-example-feature/spec.md", null, null, null, null), nl.metafactory.agents.model.RunInitiator.trigger("test"));

        var captor = org.mockito.ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(orchestrator).start(captor.capture());
        assertThat(captor.getValue().specFile()).isEqualTo("001-example-feature/spec.md");
    }

    @Test
    void startWithPromptPrefixesTheWorkflowInstructions() {
        var workflow = new WorkflowDefinition("wf-i", "Name", "Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, true,
                "Create a branch, create the spec based on the template and push for a pull request.",
                "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-i")).thenReturn(Optional.of(workflow));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-i", "Project", "spec", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));

        service.startWorkflow("wf-i", new nl.metafactory.agents.workflow.model.WorkflowStartInput(
                "Add a CSV export to the customer overview", null, null, null, null, null), nl.metafactory.agents.model.RunInitiator.trigger("test"));

        var captor = org.mockito.ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(orchestrator).start(captor.capture());
        assertThat(captor.getValue().specFile()).isEqualTo(
                "Create a branch, create the spec based on the template and push for a pull request."
                + "\n\n" + "Add a CSV export to the customer overview");
    }

    @Test
    void startWithBlankInputFallsBackToWorkflowDescription() {
        var workflow = new WorkflowDefinition("wf-b", "Name", "Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-b")).thenReturn(Optional.of(workflow));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-b", "Project", "desc", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));

        service.startWorkflow("wf-b", new nl.metafactory.agents.workflow.model.WorkflowStartInput("  ", "  ", null, null, null, null), nl.metafactory.agents.model.RunInitiator.trigger("test"));

        var captor = org.mockito.ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(orchestrator).start(captor.capture());
        assertThat(captor.getValue().specFile()).isEqualTo("desc");
    }

    @Test
    void fallsBackToProjectNameAndEmptyRepositoryUrlWhenExecutionConfigMissing() {
        var workflow = new WorkflowDefinition("wf-2", "Name", "Fallback Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-2")).thenReturn(Optional.of(workflow));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-2", "Fallback Project", "desc", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));

        service.startWorkflow("wf-2", nl.metafactory.agents.workflow.model.WorkflowStartInput.empty(), RunInitiator.trigger("test"));

        var captor = org.mockito.ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(orchestrator).start(captor.capture());
        assertThat(captor.getValue().customerId()).isEqualTo("Fallback Project");
        assertThat(captor.getValue().repositoryUrl()).isEqualTo("");
    }

    @Test
    void repositoryUrlFromStartInputWinsOverExecutionConfig() {
        var workflow = new WorkflowDefinition("wf-r", "Name", "Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, true, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-r")).thenReturn(Optional.of(workflow));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-r", "Project", "prompt", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));

        service.startWorkflow("wf-r", new nl.metafactory.agents.workflow.model.WorkflowStartInput(
                "Create a spec", null, "https://github.com/org/project-repo.git", "bot", "secret", null), nl.metafactory.agents.model.RunInitiator.trigger("test"));

        var captor = org.mockito.ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(orchestrator).start(captor.capture());
        assertThat(captor.getValue().repositoryUrl()).isEqualTo("https://github.com/org/project-repo.git");
        assertThat(captor.getValue().gitUsername()).isEqualTo("bot");
        assertThat(captor.getValue().gitToken()).isEqualTo("secret");
    }

    @Test
    void blankRepositoryUrlInStartInputFallsBackToExecutionConfig() {
        var workflow = new WorkflowDefinition("wf-rb", "Name", "Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, true, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-rb")).thenReturn(Optional.of(workflow));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-rb", "Project", "prompt", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));

        service.startWorkflow("wf-rb", new nl.metafactory.agents.workflow.model.WorkflowStartInput(
                "Create a spec", null, "  ", null, null, null), nl.metafactory.agents.model.RunInitiator.trigger("test"));

        var captor = org.mockito.ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(orchestrator).start(captor.capture());
        assertThat(captor.getValue().repositoryUrl()).isEqualTo("");
    }

    // ── MADP-54 : baseBranch threading into AgentRunRequest (AC-08 receive-half, R-9) ──

    private AgentRunRequest startAndCaptureRequest(String workflowId,
            nl.metafactory.agents.workflow.model.WorkflowStartInput input) {
        var workflow = new WorkflowDefinition(workflowId, "Name", "Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, true, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-x", "Project", "p", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));
        service.startWorkflow(workflowId, input, nl.metafactory.agents.model.RunInitiator.trigger("test"));
        var captor = org.mockito.ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(orchestrator).start(captor.capture());
        return captor.getValue();
    }

    @Test
    void baseBranchFromStartInputIsThreadedIntoTheAgentRunRequest() {
        var req = startAndCaptureRequest("wf-bb", new nl.metafactory.agents.workflow.model.WorkflowStartInput(
                "Create a spec", null, null, null, null, "develop"));

        assertThat(req.baseBranch()).isEqualTo("develop");
    }

    @Test
    void blankOrAbsentBaseBranchOnTheStartInputResolvesToNull() {
        assertThat(startAndCaptureRequest("wf-b1", new nl.metafactory.agents.workflow.model.WorkflowStartInput(
                "p", null, null, null, null, "")).baseBranch()).isNull();
    }

    @Test
    void whitespaceOnlyBaseBranchOnTheStartInputResolvesToNull() {
        assertThat(startAndCaptureRequest("wf-b2", new nl.metafactory.agents.workflow.model.WorkflowStartInput(
                "p", null, null, null, null, "   ")).baseBranch()).isNull();
    }

    @Test
    void hermesSignalStartCarriesNoBaseBranch() {
        var workflow = new WorkflowDefinition("wf-h", "Name", "Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-h")).thenReturn(Optional.of(workflow));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-h", "Project", "desc", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));

        service.startWorkflowFromHermesSignal("wf-h", "issue.created", nl.metafactory.agents.model.RunInitiator.trigger("test"));

        var captor = org.mockito.ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(orchestrator).start(captor.capture());
        assertThat(captor.getValue().baseBranch()).isNull();
    }

    @Test
    void projectFileStartCarriesNoBaseBranch() {
        var workflow = new WorkflowDefinition("wf-pf", "Name", "Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-pf")).thenReturn(Optional.of(workflow));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-pf", "Project", "desc", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));

        service.startWorkflowFromProjectFile("wf-pf", "/documents/spec.md", "CREATED", nl.metafactory.agents.model.RunInitiator.trigger("test"));

        var captor = org.mockito.ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(orchestrator).start(captor.capture());
        assertThat(captor.getValue().baseBranch()).isNull();
    }

    @Test
    void startWorkflowReturnsBlockedWhenPolicyDeniesWorkflowStart() {
        var workflow = new WorkflowDefinition("wf-3", "Name", "Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-3")).thenReturn(Optional.of(workflow));
        when(policyDecisionService.canStartWorkflow(any())).thenReturn(deny("blocked for government-sensitive project"));

        var response = service.startWorkflow("wf-3", nl.metafactory.agents.workflow.model.WorkflowStartInput.empty(), RunInitiator.trigger("test"));

        assertThat(response.status()).isEqualTo("BLOCKED");
        assertThat(response.executionId()).isNull();
        assertThat(response.message()).isEqualTo("blocked for government-sensitive project");
        verify(orchestrator, never()).start(any());
        verify(workflowRepository, never()).save(any());
    }

    @Test
    void startWorkflowExcludesAgentDeniedByPolicy() {
        var workflow = new WorkflowDefinition("wf-4", "Name", "Project", null, "desc",
                List.of("requirement", "evidence"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-4")).thenReturn(Optional.of(workflow));
        when(policyDecisionService.canUseAgent(argThat(c -> "evidence".equals(c.agentId()))))
                .thenReturn(deny("evidence publication requires approval"));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-4", "Project", "desc", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));

        // AC-20: one agent denied by policy, the rest still run — the run proceeds (not BLOCKED)
        // because resolvedAgentIds is still non-empty.
        var response = service.startWorkflow("wf-4", nl.metafactory.agents.workflow.model.WorkflowStartInput.empty(), RunInitiator.trigger("test"));

        assertThat(response.status()).isNotEqualTo("BLOCKED");

        var captor = ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(orchestrator).start(captor.capture());
        assertThat(captor.getValue().agentIds()).containsExactly("requirement");
    }

    @Test
    void subagentDenialExcludesItsParentAgent() {
        var workflow = new WorkflowDefinition("wf-5", "Name", "Project", null, "desc",
                List.of("requirement", "evidence"), List.of("log-collector"), List.of(), List.of(), null, null,
                false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-5")).thenReturn(Optional.of(workflow));
        when(subagentSpecRepository.findByName("log-collector")).thenReturn(Optional.of(
                new SubagentSpec("log-collector", "requirement", "d", "r", "i", List.of(), List.of(), "wf-5")));
        when(policyDecisionService.canUseSubagent(any())).thenReturn(deny("subagent not permitted"));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-5", "Project", "desc", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));

        service.startWorkflow("wf-5", nl.metafactory.agents.workflow.model.WorkflowStartInput.empty(), RunInitiator.trigger("test"));

        var captor = ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(orchestrator).start(captor.capture());
        assertThat(captor.getValue().agentIds()).containsExactly("evidence");
    }

    @Test
    void startWorkflowFromHermesSignalChecksHermesPolicyThenStarts() {
        var workflow = new WorkflowDefinition("wf-7", "Name", "Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-7")).thenReturn(Optional.of(workflow));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-7", "Project", "desc", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));

        var response = service.startWorkflowFromHermesSignal("wf-7", "issue.created", nl.metafactory.agents.model.RunInitiator.trigger("test"));

        assertThat(response.status()).isEqualTo("RUNNING");
        verify(policyDecisionService).canAcceptHermesSignal(any());
        verify(policyDecisionService, never()).canStartWorkflow(any());
    }

    @Test
    void startWorkflowFromHermesSignalReturnsBlockedWhenDenied() {
        var workflow = new WorkflowDefinition("wf-8", "Name", "Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-8")).thenReturn(Optional.of(workflow));
        when(policyDecisionService.canAcceptHermesSignal(any())).thenReturn(deny("signal source not trusted"));

        var response = service.startWorkflowFromHermesSignal("wf-8", "issue.created", nl.metafactory.agents.model.RunInitiator.trigger("test"));

        assertThat(response.status()).isEqualTo("BLOCKED");
        verify(orchestrator, never()).start(any());
    }

    @Test
    void startWorkflowFromProjectFileChecksFilePolicyThenStarts() {
        var workflow = new WorkflowDefinition("wf-9", "Name", "Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-9")).thenReturn(Optional.of(workflow));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-9", "Project", "desc", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));

        var response = service.startWorkflowFromProjectFile("wf-9", "/documents/spec.md", "CREATED", nl.metafactory.agents.model.RunInitiator.trigger("test"));

        assertThat(response.status()).isEqualTo("RUNNING");
        verify(policyDecisionService).canProcessProjectFile(argThat(c ->
                "/documents/spec.md".equals(c.filePath()) && "CREATED".equals(c.fileEventType())));
    }

    @Test
    void startWorkflowFromProjectFileReturnsBlockedWhenDenied() {
        var workflow = new WorkflowDefinition("wf-10", "Name", "Project", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-10")).thenReturn(Optional.of(workflow));
        when(policyDecisionService.canProcessProjectFile(any())).thenReturn(deny("file type not allowed"));

        var response = service.startWorkflowFromProjectFile("wf-10", "/documents/malware.exe", "CREATED", nl.metafactory.agents.model.RunInitiator.trigger("test"));

        assertThat(response.status()).isEqualTo("BLOCKED");
        verify(orchestrator, never()).start(any());
    }

    // ── AC-07 (risk 13's named trap) ─────────────────────────────────────────────
    // ADR-007's risk-13 trap was "a newly added field silently disappears on the post-start
    // positional re-save". That trap is now closed by construction: doStart no longer re-saves
    // at all, so no field can be dropped there. The test is retained in inverted form to pin the
    // absence of the write, keeping AC-07's original intent — a configured gate survives a run —
    // provable by a mechanism that cannot regress. ADR-007, withLastExecution and
    // WorkflowDefinitionWitherTest are all untouched; withLastExecution remains in production
    // use, now populating the pair from the database at read time (BR-6).

    @Test
    void gateConfigurationIsNeverReSavedByDoStart() {
        var gate = new ApprovalGateConfig(true, "realisation");
        var workflow = new WorkflowDefinition("wf-gate", "Name", "Project", null, "desc",
                List.of("requirement", "realisation"), List.of(), List.of(), List.of(), null, null,
                false, null, "ACTIVE", null, null, gate, null);
        when(workflowRepository.findById("wf-gate")).thenReturn(Optional.of(workflow));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-gate", "Project", "desc", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));

        service.startWorkflow("wf-gate", nl.metafactory.agents.workflow.model.WorkflowStartInput.empty(), RunInitiator.trigger("test"));

        verify(workflowRepository, never()).save(any());
    }

    @Test
    void skillDenialNowReturnsBlockedInsteadOfRunningWithEmptyAgentIds() {
        var workflow = new WorkflowDefinition("wf-6b", "Name", "Project", null, "desc", List.of("requirement", "evidence"), List.of(), List.of("summarize"), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-6b")).thenReturn(Optional.of(workflow));
        when(policyDecisionService.canExecuteSkill(any())).thenReturn(deny("skill requires human approval"));
        var response = service.startWorkflow("wf-6b", nl.metafactory.agents.workflow.model.WorkflowStartInput.empty(), RunInitiator.trigger("test"));
        assertThat(response.status()).isEqualTo("BLOCKED");
        assertThat(response.executionId()).isNull();
        assertThat(response.message()).isEqualTo("All pipeline agents of workflow 'wf-6b' were denied by policy; nothing to run.");
        verify(orchestrator, never()).start(any());
        verify(workflowRepository, never()).save(any());
    }

    @Test
    void startWorkflowReturnsBlockedWhenNoPipelineAgentsAreConfigured() {
        var workflow = new WorkflowDefinition("wf-noagents", "Name", "Project", null, "desc", List.of(), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-noagents")).thenReturn(Optional.of(workflow));

        var response = service.startWorkflow("wf-noagents", nl.metafactory.agents.workflow.model.WorkflowStartInput.empty(), RunInitiator.trigger("test"));
        assertThat(response.status()).isEqualTo("BLOCKED");
        assertThat(response.executionId()).isNull();
        assertThat(response.message()).isEqualTo("Workflow 'wf-noagents' has no pipeline agents configured; select at least one agent in Design → Workflows.");
        verify(orchestrator, never()).start(any());
    }

    @Test
    void canUseAgentIsInvokedExactlyOncePerCandidateAgent() {
        var workflow = new WorkflowDefinition("wf-ac19", "Name", "Project", null, "desc", List.of("requirement", "evidence"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        when(workflowRepository.findById("wf-ac19")).thenReturn(Optional.of(workflow));
        when(orchestrator.start(any())).thenReturn(
                new AgentRun("run-ac19", "Project", "desc", "", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null));
        service.startWorkflow("wf-ac19", nl.metafactory.agents.workflow.model.WorkflowStartInput.empty(), RunInitiator.trigger("test"));
        verify(policyDecisionService, org.mockito.Mockito.times(2)).canUseAgent(any());
    }
}
