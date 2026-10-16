package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.client.AgentRunDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.WorkflowStartInputDto;
import nl.metafactory.aicontrol.client.WorkflowStartResponseDto;
import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.GitWorkspaceJob;
import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;
import nl.metafactory.aicontrol.model.GitWorkspaceJobEvent;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.repository.GitWorkspaceJobEventRepository;
import nl.metafactory.common.oauth.ClientCredentialsTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContainerizedAgentExecutionServiceTest {

    private EmbabelAgentClient agentClient;
    private GitWorkspaceJobEventRepository eventRepo;
    private AgenticWorkflowProperties properties;
    private ContainerizedAgentExecutionService service;
    private ClientCredentialsTokens serviceTokens;

    private static final String AUTH = "Bearer svc";
    private static final String WORKSPACE = "job-ws";
    private static final String BRANCH = "agentic/wf/20261016-000000-abcd1234";

    @BeforeEach
    void setUp() {
        agentClient = mock(EmbabelAgentClient.class);
        eventRepo = mock(GitWorkspaceJobEventRepository.class);
        properties = new AgenticWorkflowProperties();
        properties.setAgentTimeoutSeconds(2);
        when(eventRepo.save(any(GitWorkspaceJobEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        serviceTokens = mock(ClientCredentialsTokens.class);
        when(serviceTokens.authorization()).thenReturn(Optional.of(AUTH));
        when(agentClient.issueGitWorkspaceTicketAs(eq(AUTH), any())).thenReturn("ticket-1");
        service = new ContainerizedAgentExecutionService(agentClient, eventRepo, properties, serviceTokens);
    }

    private GitWorkspaceJob job(String workflowId) {
        var job = new GitWorkspaceJob();
        job.setId(UUID.randomUUID());
        job.setProjectId(UUID.randomUUID());
        job.setWorkflowId(workflowId);
        job.setBaseBranch("main");
        return job;
    }

    private Project project(GitWorkspaceJob job) {
        var project = new Project();
        project.setId(job.getProjectId());
        project.setCustomerId("cust-001");
        project.setGitUrl("https://git.example/acme/app.git");
        return project;
    }

    private static Path spec(Path tempDir, String content) throws IOException {
        Path specDir = tempDir.resolve("spec");
        Files.createDirectories(specDir);
        Files.writeString(specDir.resolve("spec.md"), content);
        return specDir;
    }

    private void completedRun(String runId) {
        var run = new AgentRunDto(runId, "cust-001", "spec", "", "COMPLETED", null, null, null, null, null, null, null);
        when(agentClient.getLatestRunAs(AUTH, runId)).thenReturn(Optional.of(run));
    }

    @Test
    void runsTheJobsWorkflowBoundToTheJobWorkspaceThroughATicketForAiControlItself(@TempDir Path tempDir) throws IOException {
        var job = job("wf-spec-implement");
        Path specDir = spec(tempDir, "# Build spec");
        when(agentClient.startWorkflowAs(eq(AUTH), eq("wf-spec-implement"), any(WorkflowStartInputDto.class)))
                .thenReturn(new WorkflowStartResponseDto("wf-spec-implement", "run-xyz", "RUNNING", null, "started"));
        completedRun("run-xyz");

        service.runAgentsInsideContainer(job, "cid-001", specDir, project(job), WORKSPACE, BRANCH);

        verify(agentClient).issueGitWorkspaceTicketAs(AUTH, new EmbabelAgentClient.GitWorkspaceTicketRequest(
                WORKSPACE, "https://git.example/acme/app.git", BRANCH, "wf-spec-implement", null));
        verify(agentClient).startWorkflowAs(eq(AUTH), eq("wf-spec-implement"), argThat(input ->
                "# Build spec".equals(input.specFile()) && "https://git.example/acme/app.git".equals(input.repositoryUrl())
                        && "main".equals(input.baseBranch()) && input.prompt() == null
                        && "ticket-1".equals(input.gitWorkspaceTicket()) && input.gitToken() == null));
        // agent outputs are written and committed by embabel's per-agent checkpoints, not here
        verify(agentClient, never()).getAgentRunOutputsAs(any(), any());
    }

    @Test
    void aTicketThatCannotBeIssuedFailsTheJobBeforeTheRunStarts(@TempDir Path tempDir) throws IOException {
        var job = job("wf-1");
        when(agentClient.issueGitWorkspaceTicketAs(eq(AUTH), any())).thenThrow(new IllegalStateException("403"));

        assertThatThrownBy(() -> service.runAgentsInsideContainer(job, "cid", spec(tempDir, "# s"), project(job), WORKSPACE, BRANCH))
                .isInstanceOf(GitWorkspaceException.class).hasMessageContaining("git workspace");
        verify(agentClient, never()).startWorkflowAs(any(), any(), any());
    }

    @Test
    void withoutAServiceIdentityTheJobFailsBeforeStartingAnything(@TempDir Path tempDir) throws IOException {
        when(serviceTokens.authorization()).thenReturn(Optional.empty());
        var job = job("wf-1");

        assertThatThrownBy(() -> service.runAgentsInsideContainer(job, "cid", spec(tempDir, "# s"), project(job), WORKSPACE, BRANCH))
                .isInstanceOf(GitWorkspaceException.class).hasMessageContaining("service identity");
        verifyNoInteractions(agentClient);
    }

    @Test
    void blockedWorkflowStartFailsTheJobWithTheReason(@TempDir Path tempDir) throws IOException {
        var job = job("wf-empty");
        when(agentClient.startWorkflowAs(eq(AUTH), eq("wf-empty"), any())).thenReturn(new WorkflowStartResponseDto("wf-empty", null,
                "BLOCKED", null, "Workflow 'wf-empty' has no agents linked"));

        assertThatThrownBy(() -> service.runAgentsInsideContainer(job, "cid",
                spec(tempDir, "# spec"), project(job), WORKSPACE, BRANCH))
                .isInstanceOf(GitWorkspaceException.class)
                .hasMessageContaining("no agents linked")
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED));
    }

    @Test
    void jobWithoutWorkflowIsRejected(@TempDir Path tempDir) throws IOException {
        var job = job(null);
        assertThatThrownBy(() -> service.runAgentsInsideContainer(job, "cid",
                spec(tempDir, "# spec"), project(job), WORKSPACE, BRANCH))
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.WORKFLOW_NOT_FOUND));
        verifyNoInteractions(agentClient);
    }

    @Test
    void missingSpecFileStartsWithEmptyInput(@TempDir Path tempDir) {
        var job = job("wf-1");
        when(agentClient.startWorkflowAs(eq(AUTH), eq("wf-1"), any())).thenReturn(new WorkflowStartResponseDto("wf-1", "run-x", "RUNNING", null, null));
        completedRun("run-x");

        service.runAgentsInsideContainer(job, "cid", tempDir.resolve("spec"), project(job), WORKSPACE, BRANCH);

        verify(agentClient).startWorkflowAs(eq(AUTH), eq("wf-1"), argThat(input -> "".equals(input.specFile())));
    }

    @Test
    void continuesWhenEventSaveFails(@TempDir Path tempDir) throws IOException {
        when(eventRepo.save(any(GitWorkspaceJobEvent.class))).thenThrow(new RuntimeException("db down"));
        var job = job("wf-1");
        when(agentClient.startWorkflowAs(eq(AUTH), eq("wf-1"), any())).thenReturn(new WorkflowStartResponseDto("wf-1", "run-y", "RUNNING", null, null));
        completedRun("run-y");

        service.runAgentsInsideContainer(job, "cid", spec(tempDir, "# s"), project(job), WORKSPACE, BRANCH);

        verify(agentClient).getLatestRunAs(AUTH, "run-y");
    }

    @Test
    void pollAgentRunCompletesOnFirstPoll() {
        var run = new AgentRunDto("run-1", "", "", "", "COMPLETED", null, null, null, null, null, null, null);
        when(agentClient.getLatestRunAs(AUTH, "run-1")).thenReturn(Optional.of(run));

        service.pollAgentRun("run-1", UUID.randomUUID());

        verify(agentClient, times(1)).getLatestRunAs(AUTH, "run-1");
    }

    @Test
    void pollAgentRunThrowsOnFailedStatus() {
        var run = new AgentRunDto("run-2", "", "", "", "FAILED", null, null, null, null, null, null, null);
        when(agentClient.getLatestRunAs(AUTH, "run-2")).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.pollAgentRun("run-2", UUID.randomUUID()))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED));
    }

    @Test
    void pollAgentRunThrowsOnCancelledStatus() {
        var run = new AgentRunDto("run-3", "", "", "", "CANCELLED", null, null, null, null, null, null, null);
        when(agentClient.getLatestRunAs(AUTH, "run-3")).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.pollAgentRun("run-3", UUID.randomUUID()))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED));
    }

    @Test
    void pollAgentRunStopsAtOnceWhenTheAgentServiceLostTheRun() {
        var lost = new AgentRunDto("run-lost", "", "", "", "RUN_STATE_LOST", null, null, null, null, null, null, null);
        when(agentClient.getLatestRunAs(AUTH, "run-lost")).thenReturn(Optional.of(lost));
        properties.setAgentTimeoutSeconds(60);

        assertThatThrownBy(() -> service.pollAgentRun("run-lost", UUID.randomUUID()))
                .isInstanceOf(GitWorkspaceException.class).hasMessageContaining("RUN_STATE_LOST");
        verify(agentClient, times(1)).getLatestRunAs(AUTH, "run-lost");
    }

    @Test
    void pollAgentRunThrowsWhenRunDisappears() {
        when(agentClient.getLatestRunAs(AUTH, "run-gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pollAgentRun("run-gone", UUID.randomUUID()))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED));
    }

    @Test
    void pollAgentRunLoopsUntilTerminalStatus() {
        var running = new AgentRunDto("run-loop", "", "", "", "RUNNING", null, null, null, null, null, null, null);
        var completed = new AgentRunDto("run-loop", "", "", "", "COMPLETED", null, null, null, null, null, null, null);
        when(agentClient.getLatestRunAs(AUTH, "run-loop")).thenReturn(Optional.of(running), Optional.of(completed));
        properties.setAgentTimeoutSeconds(5);

        service.pollAgentRun("run-loop", UUID.randomUUID());

        verify(agentClient, times(2)).getLatestRunAs(AUTH, "run-loop");
    }

    @Test
    void pollAgentRunThrowsTimeoutWhenInterruptedDuringSleep() {
        var running = new AgentRunDto("run-int", "", "", "", "RUNNING", null, null, null, null, null, null, null);
        when(agentClient.getLatestRunAs(AUTH, "run-int")).thenReturn(Optional.of(running));
        properties.setAgentTimeoutSeconds(5);

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> service.pollAgentRun("run-int", UUID.randomUUID()))
                    .isInstanceOf(GitWorkspaceException.class)
                    .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                            .isEqualTo(GitWorkspaceJobErrorCode.TIMEOUT));
        } finally {
            Thread.interrupted(); // clear interrupt flag so it doesn't leak into other tests
        }
    }

    @Test
    void pollAgentRunThrowsOnTimeoutAfterDeadline() {
        var inProgressRun = new AgentRunDto("run-slow", "", "", "", "RUNNING", null, null, null, null, null, null, null);
        when(agentClient.getLatestRunAs(AUTH, "run-slow")).thenReturn(Optional.of(inProgressRun));

        properties.setAgentTimeoutSeconds(0);
        assertThatThrownBy(() -> service.pollAgentRun("run-slow", UUID.randomUUID()))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.TIMEOUT));
        verify(agentClient).stopAgentRunAs(AUTH, "run-slow");
    }

    @Test
    void aTimedOutRunThatCannotBeStoppedStillTimesOut() {
        var inProgressRun = new AgentRunDto("run-slow", "", "", "", "RUNNING", null, null, null, null, null, null, null);
        when(agentClient.getLatestRunAs(AUTH, "run-slow")).thenReturn(Optional.of(inProgressRun));
        doThrow(new IllegalStateException("agent service down")).when(agentClient).stopAgentRunAs(AUTH, "run-slow");

        properties.setAgentTimeoutSeconds(0);
        assertThatThrownBy(() -> service.pollAgentRun("run-slow", UUID.randomUUID()))
                .isInstanceOfSatisfying(GitWorkspaceException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GitWorkspaceJobErrorCode.TIMEOUT));
    }

    @Test
    void aServiceTokenThatCannotBeObtainedFailsTheAgentExecution() {
        when(serviceTokens.authorization()).thenThrow(new IllegalStateException("token endpoint down"));

        assertThatThrownBy(() -> service.serviceAuthorization())
                .isInstanceOfSatisfying(GitWorkspaceException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED);
                    assertThat(e.getMessage()).endsWith("ai-control service identity token could not be obtained");
                });
    }
}
