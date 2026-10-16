package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.WorkflowStartInputDto;
import nl.metafactory.aicontrol.client.WorkflowStartResponseDto;
import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import nl.metafactory.common.oauth.ClientCredentialsTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProjectWorkflowRunServiceTest {

    private static final String AUTH = "Bearer svc";
    private static final String REPO = "https://github.com/org/repo.git";

    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final GitWorkspaceJobService jobs = mock(GitWorkspaceJobService.class);
    private final EmbabelAgentClient client = mock(EmbabelAgentClient.class);
    private final ClientCredentialsTokens tokens = mock(ClientCredentialsTokens.class);
    private final ProjectWorkflowRunService service = new ProjectWorkflowRunService(projects, jobs, client, tokens);

    private final UUID projectId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final GitWorkspaceJobService.PreparedWorkspace workspace =
            new GitWorkspaceJobService.PreparedWorkspace(jobId, "job-" + jobId, REPO, "agentic/wf-1/x");

    @BeforeEach
    void setUp() {
        var project = new Project();
        project.setId(projectId);
        project.setGitUrl(REPO);
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(tokens.authorization()).thenReturn(Optional.of(AUTH));
        when(jobs.prepareWorkflowRunWorkspace(eq(projectId), eq("wf-1"), any(), any())).thenReturn(workspace);
        when(client.issueGitWorkspaceTicketAs(eq(AUTH), any())).thenReturn("ticket-1");
        var jwt = Jwt.withTokenValue("user-token").header("alg", "RS256").subject("alice-sub")
                .claim("preferred_username", "alice").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private WorkflowStartInputDto input() {
        return new WorkflowStartInputDto(null, "# Spec: specs/spec-1.md\n\n...", REPO, projectId.toString(), null, null, "main");
    }

    @Test
    void preparesTheWorkspaceGetsATicketForTheUserAndStartsTheRunAsTheUser() {
        when(client.startWorkflow(eq("wf-1"), any())).thenReturn(
                new WorkflowStartResponseDto("wf-1", "run-1", "RUNNING", Instant.now(), "Workflow started"));

        var response = service.startOnProjectWorkspace("wf-1", "spec-1.md", input()).orElseThrow();

        verify(jobs).prepareWorkflowRunWorkspace(projectId, "wf-1", "spec-1.md", "alice");
        verify(client).issueGitWorkspaceTicketAs(AUTH, new EmbabelAgentClient.GitWorkspaceTicketRequest(
                "job-" + jobId, REPO, "agentic/wf-1/x", "wf-1", "alice-sub"));
        // started with the relayed user token (startWorkflow, not startWorkflowAs) and the ticket
        verify(client).startWorkflow(eq("wf-1"), argThat(i -> "ticket-1".equals(i.gitWorkspaceTicket())));
        verify(client, never()).startWorkflowAs(any(), any(), any());
        verify(jobs).finishWorkflowRunAsync(workspace, "run-1");
        assertThat(response.executionId()).isEqualTo("run-1");
        assertThat(response.message()).contains("agentic/wf-1/x");
    }

    @Test
    void aBlockedStartEndsTheJobAndReturnsTheReason() {
        when(client.startWorkflow(eq("wf-1"), any())).thenReturn(
                new WorkflowStartResponseDto("wf-1", null, "BLOCKED", Instant.now(), "denied by policy"));

        var response = service.startOnProjectWorkspace("wf-1", "spec-1.md", input()).orElseThrow();

        assertThat(response.status()).isEqualTo("BLOCKED");
        verify(jobs).workflowRunNotStarted(jobId, "denied by policy");
        verify(jobs, never()).finishWorkflowRunAsync(any(), any());
    }

    @Test
    void withoutAProjectRepositoryThereIsNoWorkspace() {
        assertThat(service.startOnProjectWorkspace("wf-1", null, WorkflowStartInputDto.empty())).isEmpty();
        var noRepo = new Project();
        noRepo.setId(projectId);
        when(projects.findById(projectId)).thenReturn(Optional.of(noRepo));
        assertThat(service.startOnProjectWorkspace("wf-1", null, input())).isEmpty();
        verifyNoInteractions(jobs, client);
    }

    @Test
    void aCloneFailureIsABadGatewayAndNothingStarts() {
        when(jobs.prepareWorkflowRunWorkspace(any(), any(), any(), any()))
                .thenThrow(new GitWorkspaceException(GitWorkspaceJobErrorCode.GIT_CLONE_FAILED, "auth failed"));

        assertThatThrownBy(() -> service.startOnProjectWorkspace("wf-1", "spec-1.md", input()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
        verifyNoInteractions(client);
    }

    @Test
    void aTicketFailureEndsTheJobAndNothingStarts() {
        when(client.issueGitWorkspaceTicketAs(any(), any())).thenThrow(new IllegalStateException("403"));

        assertThatThrownBy(() -> service.startOnProjectWorkspace("wf-1", "spec-1.md", input()))
                .isInstanceOf(ResponseStatusException.class);
        verify(jobs).workflowRunNotStarted(eq(jobId), any());
        verify(client, never()).startWorkflow(any(), any());
    }

    @Test
    void withoutServiceIdentityOrUserNothingIsPrepared() {
        when(tokens.authorization()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.startOnProjectWorkspace("wf-1", "spec-1.md", input()))
                .isInstanceOf(ResponseStatusException.class);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.startOnProjectWorkspace("wf-1", "spec-1.md", input()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verifyNoInteractions(jobs);
    }

    @Test
    void theJobRecordsTheSelectedSpecNameNotItsContent() {
        assertThat(ProjectWorkflowRunService.specRef("wf-1", "spec-1.md")).isEqualTo("spec-1.md");
        assertThat(ProjectWorkflowRunService.specRef("wf-1", "# Spec\n\nbody")).isEqualTo("workflow:wf-1");
        assertThat(ProjectWorkflowRunService.specRef("wf-1", null)).isEqualTo("workflow:wf-1");
    }
}
