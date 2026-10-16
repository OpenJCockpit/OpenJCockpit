package nl.metafactory.agents.workflow;

import nl.metafactory.agents.gitworkspace.GitWorkspaceBinding;
import nl.metafactory.agents.gitworkspace.GitWorkspaceProperties;
import nl.metafactory.agents.gitworkspace.GitWorkspaceTickets;
import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import nl.metafactory.agents.policy.PolicyDecisionService;
import nl.metafactory.agents.policy.model.PolicyDecision;
import nl.metafactory.agents.security.CurrentUserProvider;
import nl.metafactory.agents.workflow.model.ExecutionConfig;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowStartInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A run is bound to a git workspace only through a ticket ai-control issued for exactly this workflow and
 * the subject that starts it; any other ticket blocks the start.
 */
class WorkflowExecutionServiceGitWorkspaceTest {

    private static final String REPO = "https://github.com/org/repo.git";
    private static final GitWorkspaceBinding WORKSPACE = new GitWorkspaceBinding("job-1", REPO, "openjcockpit/wf-1/abc");
    private static final RunInitiator ALICE = RunInitiator.human("alice", "alice-sub");

    private AgentOrchestrator orchestrator;
    private GitWorkspaceTickets tickets;
    private WorkflowExecutionService service;

    @BeforeEach
    void setUp() {
        var repository = mock(WorkflowDefinitionRepository.class);
        orchestrator = mock(AgentOrchestrator.class);
        var policy = mock(PolicyDecisionService.class);
        var allow = new PolicyDecision(true, "allowed", false, "low", List.of(), null, null, null, Instant.now(), false, null);
        when(policy.canStartWorkflow(any())).thenReturn(allow);
        when(policy.canUseAgent(any())).thenReturn(allow);
        var users = mock(CurrentUserProvider.class);
        when(users.currentUsername()).thenReturn(Optional.of("alice"));
        when(repository.findById("wf-1")).thenReturn(Optional.of(new WorkflowDefinition("wf-1", "Impl", "P", null, "d",
                List.of("a2a:litellm:software-developer"), List.of(), List.of(), List.of(), null,
                new ExecutionConfig("cust1", "", 300), false, null, "ACTIVE", null, null, null, null)));
        var run = new AgentRun("run-1", "cust1", "spec", REPO, "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null);
        when(orchestrator.start(any())).thenReturn(run);
        when(orchestrator.start(any(), any())).thenReturn(run);
        tickets = new GitWorkspaceTickets(new GitWorkspaceProperties());
        service = new WorkflowExecutionService(repository, mock(AgentSpecRepository.class), mock(SubagentSpecRepository.class),
                mock(SkillSpecRepository.class), orchestrator, policy, users, tickets);
    }

    private static WorkflowStartInput withTicket(String ticket) {
        return new WorkflowStartInput(null, "# spec", null, null, null, "main", ticket);
    }

    @Test
    void aTicketForThisWorkflowAndUserBindsTheRunToItsWorkspace() {
        String ticket = tickets.issue(WORKSPACE, "wf-1", "alice-sub").ticket();

        var response = service.startWorkflow("wf-1", withTicket(ticket), ALICE);

        assertThat(response.executionId()).isEqualTo("run-1");
        verify(orchestrator).start(any(), eq(WORKSPACE));
    }

    @Test
    void aTicketIssuedForSomeoneElseBlocksTheStartAndIsUsedUp() {
        String ticket = tickets.issue(WORKSPACE, "wf-1", "bob-sub").ticket();

        var response = service.startWorkflow("wf-1", withTicket(ticket), ALICE);

        assertThat(response.status()).isEqualTo("BLOCKED");
        verify(orchestrator, never()).start(any());
        verify(orchestrator, never()).start(any(), any());
        assertThat(tickets.redeem(ticket, "wf-1", "bob-sub")).isEmpty();
    }

    @Test
    void aTicketForAnotherWorkflowBlocksTheStart() {
        String ticket = tickets.issue(WORKSPACE, "wf-other", "alice-sub").ticket();

        assertThat(service.startWorkflow("wf-1", withTicket(ticket), ALICE).status()).isEqualTo("BLOCKED");
    }

    @Test
    void anUnknownTicketBlocksTheStart() {
        assertThat(service.startWorkflow("wf-1", withTicket("made-up"), ALICE).status()).isEqualTo("BLOCKED");
        verify(orchestrator, never()).start(any(), any());
    }

    @Test
    void aStartWithoutTicketIsUnbound() {
        service.startWorkflow("wf-1", WorkflowStartInput.empty(), ALICE);

        verify(orchestrator).start(any());
        verify(orchestrator, never()).start(any(), any());
    }
}
