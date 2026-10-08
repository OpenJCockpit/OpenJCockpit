package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.client.AgentDefinitionDto;
import nl.metafactory.aicontrol.client.AgentRunDto;
import nl.metafactory.aicontrol.client.AgentRunRequestDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.GitWorkspaceJob;
import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;
import nl.metafactory.aicontrol.model.GitWorkspaceJobEvent;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.repository.GitWorkspaceJobEventRepository;
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

    @BeforeEach
    void setUp() {
        agentClient = mock(EmbabelAgentClient.class);
        eventRepo = mock(GitWorkspaceJobEventRepository.class);
        properties = new AgenticWorkflowProperties();
        properties.setAgentTimeoutSeconds(2);
        when(eventRepo.save(any(GitWorkspaceJobEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        service = new ContainerizedAgentExecutionService(agentClient, eventRepo, properties);
    }

    @Test
    void runAgentsInsideContainerStartsAndPolls(@TempDir Path tempDir) throws IOException {
        UUID jobId = UUID.randomUUID();
        Path specDir = tempDir.resolve("spec");
        Files.createDirectories(specDir);
        Files.writeString(specDir.resolve("spec.md"), "# Build spec");
        Path repoDir = tempDir.resolve("repo");
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        var job = new GitWorkspaceJob();
        job.setId(jobId);
        job.setProjectId(UUID.randomUUID());

        var project = new Project();
        project.setId(job.getProjectId());
        project.setCustomerId("cust-001");

        var agentDef = new AgentDefinitionDto("agent-1", "Coder", "", "coder", 1, "SPEC", "CODE");
        when(agentClient.getAgentDefinitions()).thenReturn(List.of(agentDef));

        var completedRun = new AgentRunDto("run-xyz", "cust-001", "spec", "/repo", "COMPLETED",
                null, null, null, null, null, null, null);
        when(agentClient.startAgentRun(any(AgentRunRequestDto.class))).thenReturn(completedRun);
        when(agentClient.getLatestRun("run-xyz")).thenReturn(Optional.of(completedRun));

        service.runAgentsInsideContainer(job, "cid-001", repoDir, specDir, outputDir, project);

        verify(agentClient).startAgentRun(argThat(req ->
                "cust-001".equals(req.customerId()) && repoDir.toString().equals(req.repositoryUrl())));
    }

    @Test
    void runAgentsInsideContainerThrowsWhenStartFails(@TempDir Path tempDir) throws IOException {
        UUID jobId = UUID.randomUUID();
        Path specDir = tempDir.resolve("spec");
        Files.createDirectories(specDir);
        Files.writeString(specDir.resolve("spec.md"), "# spec");

        var job = new GitWorkspaceJob();
        job.setId(jobId);
        job.setProjectId(UUID.randomUUID());

        var project = new Project();
        project.setId(job.getProjectId());

        when(agentClient.getAgentDefinitions()).thenReturn(List.of());
        when(agentClient.startAgentRun(any())).thenReturn(null);

        assertThatThrownBy(() -> service.runAgentsInsideContainer(job, "cid",
                tempDir.resolve("repo"), specDir, tempDir.resolve("output"), project))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED));
    }

    @Test
    void pollAgentRunCompletesOnFirstPoll() {
        var run = new AgentRunDto("run-1", "", "", "", "COMPLETED", null, null, null, null, null, null, null);
        when(agentClient.getLatestRun("run-1")).thenReturn(Optional.of(run));

        service.pollAgentRun("run-1", UUID.randomUUID());

        verify(agentClient, times(1)).getLatestRun("run-1");
    }

    @Test
    void pollAgentRunThrowsOnFailedStatus() {
        var run = new AgentRunDto("run-2", "", "", "", "FAILED", null, null, null, null, null, null, null);
        when(agentClient.getLatestRun("run-2")).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.pollAgentRun("run-2", UUID.randomUUID()))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED));
    }

    @Test
    void pollAgentRunThrowsOnCancelledStatus() {
        var run = new AgentRunDto("run-3", "", "", "", "CANCELLED", null, null, null, null, null, null, null);
        when(agentClient.getLatestRun("run-3")).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.pollAgentRun("run-3", UUID.randomUUID()))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED));
    }

    @Test
    void pollAgentRunThrowsWhenRunDisappears() {
        when(agentClient.getLatestRun("run-gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pollAgentRun("run-gone", UUID.randomUUID()))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED));
    }

    @Test
    void pollAgentRunLoopsUntilTerminalStatus() {
        var running = new AgentRunDto("run-loop", "", "", "", "RUNNING", null, null, null, null, null, null, null);
        var completed = new AgentRunDto("run-loop", "", "", "", "COMPLETED", null, null, null, null, null, null, null);
        when(agentClient.getLatestRun("run-loop")).thenReturn(Optional.of(running), Optional.of(completed));
        properties.setAgentTimeoutSeconds(5);

        service.pollAgentRun("run-loop", UUID.randomUUID());

        verify(agentClient, times(2)).getLatestRun("run-loop");
    }

    @Test
    void pollAgentRunThrowsTimeoutWhenInterruptedDuringSleep() {
        var running = new AgentRunDto("run-int", "", "", "", "RUNNING", null, null, null, null, null, null, null);
        when(agentClient.getLatestRun("run-int")).thenReturn(Optional.of(running));
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
        when(agentClient.getLatestRun("run-slow")).thenReturn(Optional.of(inProgressRun));

        properties.setAgentTimeoutSeconds(0);
        assertThatThrownBy(() -> service.pollAgentRun("run-slow", UUID.randomUUID()))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.TIMEOUT));
    }

    @Test
    void runAgentsUsesProjectIdAsCustomerIdWhenCustomerIdNull(@TempDir Path tempDir) throws IOException {
        UUID jobId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Path specDir = tempDir.resolve("spec");
        Files.createDirectories(specDir);
        Files.writeString(specDir.resolve("spec.md"), "# spec");

        var job = new GitWorkspaceJob();
        job.setId(jobId);
        job.setProjectId(projectId);

        var project = new Project();
        project.setId(projectId);
        project.setCustomerId(null);

        when(agentClient.getAgentDefinitions()).thenReturn(List.of());
        when(agentClient.startAgentRun(any())).thenReturn(null);

        assertThatThrownBy(() -> service.runAgentsInsideContainer(job, null,
                tempDir.resolve("repo"), specDir, tempDir.resolve("output"), project))
                .isInstanceOf(GitWorkspaceException.class);

        verify(agentClient).startAgentRun(argThat(req -> projectId.toString().equals(req.customerId())));
    }

    @Test
    void runAgentsInsideContainerUsesEmptySpecContentWhenFileMissing(@TempDir Path tempDir) throws IOException {
        UUID jobId = UUID.randomUUID();
        Path specDir = tempDir.resolve("spec"); // never created, no spec.md written
        Path repoDir = tempDir.resolve("repo");
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        var job = new GitWorkspaceJob();
        job.setId(jobId);
        job.setProjectId(UUID.randomUUID());

        var project = new Project();
        project.setId(job.getProjectId());
        project.setCustomerId("cust-1");

        when(agentClient.getAgentDefinitions()).thenReturn(List.of());
        var completedRun = new AgentRunDto("run-x", "cust-1", "", "", "COMPLETED", null, null, null, null, null, null, null);
        when(agentClient.startAgentRun(any())).thenReturn(completedRun);
        when(agentClient.getLatestRun("run-x")).thenReturn(Optional.of(completedRun));

        service.runAgentsInsideContainer(job, "cid", repoDir, specDir, outputDir, project);

        verify(agentClient).startAgentRun(argThat(req -> "".equals(req.specFile())));
    }

    @Test
    void runAgentsInsideContainerContinuesWhenEventSaveFails(@TempDir Path tempDir) throws IOException {
        UUID jobId = UUID.randomUUID();
        Path specDir = tempDir.resolve("spec");
        Files.createDirectories(specDir);
        Files.writeString(specDir.resolve("spec.md"), "# Build spec");
        Path repoDir = tempDir.resolve("repo");
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        var job = new GitWorkspaceJob();
        job.setId(jobId);
        job.setProjectId(UUID.randomUUID());

        var project = new Project();
        project.setId(job.getProjectId());
        project.setCustomerId("cust-001");

        when(eventRepo.save(any(GitWorkspaceJobEvent.class))).thenThrow(new RuntimeException("db down"));

        var agentDef = new AgentDefinitionDto("agent-1", "Coder", "", "coder", 1, "SPEC", "CODE");
        when(agentClient.getAgentDefinitions()).thenReturn(List.of(agentDef));
        var completedRun = new AgentRunDto("run-xyz", "cust-001", "spec", "/repo", "COMPLETED",
                null, null, null, null, null, null, null);
        when(agentClient.startAgentRun(any(AgentRunRequestDto.class))).thenReturn(completedRun);
        when(agentClient.getLatestRun("run-xyz")).thenReturn(Optional.of(completedRun));

        service.runAgentsInsideContainer(job, "cid-001", repoDir, specDir, outputDir, project);

        verify(agentClient).startAgentRun(any());
    }
}