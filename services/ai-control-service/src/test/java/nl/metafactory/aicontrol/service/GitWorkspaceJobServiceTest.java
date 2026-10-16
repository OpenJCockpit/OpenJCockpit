package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.*;
import nl.metafactory.aicontrol.repository.GitWorkspaceJobEventRepository;
import nl.metafactory.aicontrol.repository.GitWorkspaceJobRepository;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import nl.metafactory.common.mcp.GitMcpGateway;
import nl.metafactory.common.mcp.LiteLlmMcpToolClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GitWorkspaceJobServiceTest {

    private GitWorkspaceJobRepository jobRepo;
    private GitWorkspaceJobEventRepository eventRepo;
    private ProjectRepository projectRepo;
    private ProjectGitCredentialRepository credRepo;
    private BranchNameGenerator branchNameGenerator;
    private SpecFileService specFileService;
    private GitOperationService gitOps;
    private WorkflowContainerService containerService;
    private ContainerizedAgentExecutionService agentService;
    private AgenticWorkflowProperties properties;
    private GitMcpGateway gitMcp;
    private GitWorkspaceJobService service;

    private UUID projectId;
    private UUID jobId;
    private Project project;
    private GitWorkspaceJob job;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        jobRepo = mock(GitWorkspaceJobRepository.class);
        eventRepo = mock(GitWorkspaceJobEventRepository.class);
        projectRepo = mock(ProjectRepository.class);
        credRepo = mock(ProjectGitCredentialRepository.class);
        branchNameGenerator = mock(BranchNameGenerator.class);
        specFileService = mock(SpecFileService.class);
        gitOps = mock(GitOperationService.class);
        containerService = mock(WorkflowContainerService.class);
        agentService = mock(ContainerizedAgentExecutionService.class);
        gitMcp = mock(GitMcpGateway.class);

        properties = new AgenticWorkflowProperties();
        properties.setWorkspaceBasePath(tempDir.toString());
        properties.setDefaultBaseBranch("main");

        service = new GitWorkspaceJobService(jobRepo, eventRepo,
                new ProjectGitResolver(projectRepo, credRepo, properties),
                branchNameGenerator, specFileService, gitOps, containerService, agentService, properties, gitMcp);

        projectId = UUID.randomUUID();
        jobId = UUID.randomUUID();

        project = new Project();
        project.setId(projectId);
        project.setName("Test Project");
        project.setActive((short) 1);
        project.setGitUrl("https://github.com/org/repo");
        project.setDefaultBranch("main");

        job = new GitWorkspaceJob();
        job.setId(jobId);
        job.setProjectId(projectId);
        job.setSpecFileRef("spec-001");
        job.setBaseBranch("main");
        job.setStatus(GitWorkspaceJobStatus.CREATED);

        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jobRepo.existsById(jobId)).thenReturn(true);
        when(eventRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        when(credRepo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(any(), anyShort()))
                .thenReturn(Optional.empty());
        when(containerService.createWorkflowContainer(any(), any())).thenReturn("cid-test");
        when(branchNameGenerator.generate(any(), any())).thenReturn("agentic/test-project/20260703-123456-a1b2c3d4");
        when(gitOps.maskUrl(any())).thenReturn("https://github.com/org/repo");
        when(gitMcp.createBranch(any(), any(), any(), any())).thenReturn(ok(null));
        when(gitMcp.commit(any(), any(), any(), any(), any())).thenReturn(ok("abc123commit"));
        when(gitMcp.push(any(), any(), any())).thenReturn(ok(null));
    }

    private static GitMcpGateway.Result ok(String commitHash) {
        return new GitMcpGateway.Result(true, "ok", "branch", commitHash);
    }

    private String workspaceKey() {
        return "job-" + jobId;
    }

    // ── createJob validation ────────────────────────────────────────────────

    @Test
    void createJobFailsWhenProjectIdNull() {
        assertThatThrownBy(() -> service.createJob(null, "spec", "wf-1", "user"))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.NO_SELECTED_PROJECT));
    }

    @Test
    void createJobFailsWhenProjectNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(projectRepo.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createJob(unknownId, "spec", "wf-1", "user"))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.PROJECT_NOT_FOUND));
    }

    @Test
    void createJobFailsWhenProjectInactive() {
        project.setActive((short) 0);

        assertThatThrownBy(() -> service.createJob(projectId, "spec", "wf-1", "user"))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.PROJECT_INACTIVE));
    }

    @Test
    void createJobFailsWhenGitUrlMissing() {
        project.setGitUrl(null);

        assertThatThrownBy(() -> service.createJob(projectId, "spec", "wf-1", "user"))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.GIT_URL_MISSING));
    }

    @Test
    void createJobFailsWhenGitUrlBlank() {
        project.setGitUrl("  ");

        assertThatThrownBy(() -> service.createJob(projectId, "spec", "wf-1", "user"))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.GIT_URL_MISSING));
    }

    @Test
    void createJobPersistsAndReturnsDto() {
        var dto = service.createJob(projectId, "spec-001", "wf-1", "alice");

        verify(jobRepo).save(argThat((GitWorkspaceJob job) -> "wf-1".equals(job.getWorkflowId())));
        assertThat(dto.workflowId()).isEqualTo("wf-1");
        verify(eventRepo, atLeastOnce()).save(any());
    }

    @Test
    void createJobUsesDefaultBaseBranchWhenProjectHasNone() {
        project.setDefaultBranch(null);
        properties.setDefaultBaseBranch("develop");

        var dto = service.createJob(projectId, "spec-001", "wf-1", "alice");

        assertThat(dto.baseBranch()).isEqualTo("develop");
    }

    @Test
    void createJobUsesDefaultBaseBranchWhenProjectBranchBlank() {
        project.setDefaultBranch("   ");
        properties.setDefaultBaseBranch("develop");

        var dto = service.createJob(projectId, "spec-001", "wf-1", "alice");

        assertThat(dto.baseBranch()).isEqualTo("develop");
    }

    // ── runWorkflowAsync: happy path ────────────────────────────────────────

    @Test
    void runWorkflowAsyncHappyPathCleansUpWorkspace() throws Exception {
        var spec = new SpecFile("spec-001", "build.md", "", "", "", false, "# Content", "");
        when(specFileService.resolveSpecFile("spec-001")).thenReturn(spec);

        service.runWorkflowAsync(jobId);

        verify(containerService).cleanupContainer("cid-test");
        // clone + branch, commit and push all happen in the job's git MCP workspace, via LiteLLM
        verify(gitMcp).createBranch("https://github.com/org/repo", "main", "agentic/test-project/20260703-123456-a1b2c3d4", workspaceKey());
        verify(gitMcp).commit(eq("https://github.com/org/repo"), any(), eq("Agentic Workflow"),
                eq("agentic@metafactory.nl"), eq(workspaceKey()));
        verify(gitMcp).push("https://github.com/org/repo", "agentic/test-project/20260703-123456-a1b2c3d4", workspaceKey());
        assertThat(job.getStatus()).isEqualTo(GitWorkspaceJobStatus.COMPLETED);
        assertThat(job.getCommitHash()).isEqualTo("abc123commit");
        verifyNoInteractions(credRepo);
    }

    @Test
    void runWorkflowAsyncDeletesWorkspaceOnSuccess() throws Exception {
        var spec = new SpecFile("spec-001", "build.md", "", "", "", false, "# Content", "");
        when(specFileService.resolveSpecFile("spec-001")).thenReturn(spec);

        service.runWorkflowAsync(jobId);

        Path expectedDir = Path.of(properties.getWorkspaceBasePath()).resolve("job-" + jobId);
        assertThat(expectedDir).doesNotExist();
    }

    // ── runWorkflowAsync: clone failure ─────────────────────────────────────

    @Test
    void runWorkflowAsyncCleansUpOnCloneFailure() throws Exception {
        var spec = new SpecFile("spec-001", "build.md", "", "", "", false, "# Content", "");
        when(specFileService.resolveSpecFile("spec-001")).thenReturn(spec);
        when(gitMcp.createBranch(any(), any(), any(), any()))
                .thenThrow(new LiteLlmMcpToolClient.McpCallException("MCP tool git_create_branch via LiteLLM failed with HTTP 502"));

        service.runWorkflowAsync(jobId);

        assertThat(job.getErrorCode()).isEqualTo(GitWorkspaceJobErrorCode.GIT_CLONE_FAILED);
        verifyNoInteractions(agentService);

        verify(containerService).cleanupContainer("cid-test");
        Path expectedDir = Path.of(properties.getWorkspaceBasePath()).resolve("job-" + jobId);
        assertThat(expectedDir).doesNotExist();
    }

    // ── runWorkflowAsync: agent failure ─────────────────────────────────────

    @Test
    void runWorkflowAsyncCleansUpOnAgentFailure() throws Exception {
        var spec = new SpecFile("spec-001", "build.md", "", "", "", false, "# Content", "");
        when(specFileService.resolveSpecFile("spec-001")).thenReturn(spec);
        doThrow(new GitWorkspaceException(GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED, "agent error"))
                .when(agentService).runAgentsInsideContainer(any(), any(), any(), any(), any(), any());

        service.runWorkflowAsync(jobId);

        verify(containerService).cleanupContainer("cid-test");
        Path expectedDir = Path.of(properties.getWorkspaceBasePath()).resolve("job-" + jobId);
        assertThat(expectedDir).doesNotExist();
    }

    // ── runWorkflowAsync: push failure ──────────────────────────────────────

    @Test
    void runWorkflowAsyncCleansUpOnPushFailure() throws Exception {
        var spec = new SpecFile("spec-001", "build.md", "", "", "", false, "# Content", "");
        when(specFileService.resolveSpecFile("spec-001")).thenReturn(spec);
        when(gitMcp.push(any(), any(), any())).thenReturn(new GitMcpGateway.Result(false,
                "git_push failed: workspace credentials unavailable", null, null));

        service.runWorkflowAsync(jobId);

        assertThat(job.getErrorCode()).isEqualTo(GitWorkspaceJobErrorCode.PUSH_FAILED);

        verify(containerService).cleanupContainer("cid-test");
        Path expectedDir = Path.of(properties.getWorkspaceBasePath()).resolve("job-" + jobId);
        assertThat(expectedDir).doesNotExist();
    }

    // ── runWorkflowAsync: no changes ────────────────────────────────────────

    @Test
    void nothingLeftToCommitAfterTheCheckpointsStillPushesAndCompletes() throws Exception {
        var spec = new SpecFile("spec-001", "build.md", "", "", "", false, "# Content", "");
        when(specFileService.resolveSpecFile("spec-001")).thenReturn(spec);
        when(gitMcp.commit(any(), any(), any(), any(), any())).thenReturn(new GitMcpGateway.Result(false,
                "Nothing to commit: workspace has no uncommitted changes", null, null));

        service.runWorkflowAsync(jobId);

        assertThat(job.getStatus()).isEqualTo(GitWorkspaceJobStatus.COMPLETED);
        verify(gitMcp).push("https://github.com/org/repo", "agentic/test-project/20260703-123456-a1b2c3d4", workspaceKey());
        verify(containerService).cleanupContainer("cid-test");
    }

    // ── workflow runs started from the dashboard on a project ────────────────

    private UUID newJobsGetAnId() {
        UUID created = UUID.randomUUID();
        when(jobRepo.save(any())).thenAnswer(inv -> {
            GitWorkspaceJob saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(created);
                when(jobRepo.findById(created)).thenReturn(Optional.of(saved));
            }
            return saved;
        });
        return created;
    }

    @Test
    void aDashboardRunGetsAClonedWorkspaceOnABranchNamedAfterTheWorkflow() {
        UUID created = newJobsGetAnId();
        when(branchNameGenerator.forWorkflowRun(eq("wf-spec-realise"), any())).thenReturn("agentic/wf-spec-realise/x");

        var ws = service.prepareWorkflowRunWorkspace(projectId, "wf-spec-realise", "spec-1.md", "alice");

        assertThat(ws).isEqualTo(new GitWorkspaceJobService.PreparedWorkspace(created, "job-" + created,
                "https://github.com/org/repo", "agentic/wf-spec-realise/x"));
        verify(gitMcp).createBranch("https://github.com/org/repo", "main", "agentic/wf-spec-realise/x", "job-" + created);
        var saved = jobRepo.findById(created).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(GitWorkspaceJobStatus.BRANCH_CREATED);
        assertThat(saved.getStartedBy()).isEqualTo("alice");
        assertThat(saved.getWorkflowId()).isEqualTo("wf-spec-realise");
        verifyNoInteractions(containerService, agentService);
    }

    @Test
    void aWorkspaceThatCannotBeClonedFailsTheJobAndTheStart() {
        UUID created = newJobsGetAnId();
        when(branchNameGenerator.forWorkflowRun(any(), any())).thenReturn("agentic/wf/x");
        when(gitMcp.createBranch(any(), any(), any(), any())).thenReturn(new GitMcpGateway.Result(false, "auth failed", null, null));

        assertThatThrownBy(() -> service.prepareWorkflowRunWorkspace(projectId, "wf", "spec", "alice"))
                .isInstanceOf(GitWorkspaceException.class).hasMessageContaining("auth failed");
        assertThat(jobRepo.findById(created).orElseThrow().getStatus()).isEqualTo(GitWorkspaceJobStatus.FAILED);
    }

    @Test
    void aFinishedDashboardRunGetsAFinalCommitAndPushOfItsBranch() {
        job.setWorkflowId("wf-1");
        var ws = new GitWorkspaceJobService.PreparedWorkspace(jobId, workspaceKey(), "https://github.com/org/repo", "agentic/wf-1/x");

        service.finishWorkflowRunAsync(ws, "run-1");

        var order = inOrder(agentService, gitMcp);
        order.verify(agentService).pollAgentRun("run-1", jobId);
        order.verify(gitMcp).commit(eq("https://github.com/org/repo"), any(), any(), any(), eq(workspaceKey()));
        order.verify(gitMcp).push("https://github.com/org/repo", "agentic/wf-1/x", workspaceKey());
        assertThat(job.getStatus()).isEqualTo(GitWorkspaceJobStatus.COMPLETED);
        assertThat(job.getCommitHash()).isEqualTo("abc123commit");
    }

    @Test
    void aDashboardRunThatDoesNotCompleteFailsTheJobWithoutAFinalPush() {
        var ws = new GitWorkspaceJobService.PreparedWorkspace(jobId, workspaceKey(), "https://github.com/org/repo", "agentic/wf-1/x");
        doThrow(new GitWorkspaceException(GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED, "Agent run ended with status: FAILED"))
                .when(agentService).pollAgentRun("run-1", jobId);

        service.finishWorkflowRunAsync(ws, "run-1");

        assertThat(job.getStatus()).isEqualTo(GitWorkspaceJobStatus.FAILED);
        verify(gitMcp, never()).push(any(), any(), any());
    }

    @Test
    void anUnexpectedErrorPreparingTheWorkspaceFailsTheJobWithoutItsDetail() {
        UUID created = newJobsGetAnId();
        when(branchNameGenerator.forWorkflowRun(any(), any())).thenReturn("agentic/wf/x");
        when(gitMcp.createBranch(any(), any(), any(), any())).thenThrow(new IllegalStateException("secret detail"));

        assertThatThrownBy(() -> service.prepareWorkflowRunWorkspace(projectId, "wf", "spec", "alice"))
                .isInstanceOfSatisfying(GitWorkspaceException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(GitWorkspaceJobErrorCode.GIT_CLONE_FAILED);
                    assertThat(e.getMessage()).endsWith("The project repository could not be prepared");
                });
        var saved = jobRepo.findById(created).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(GitWorkspaceJobStatus.FAILED);
        assertThat(saved.getErrorMessage()).isEqualTo("Unexpected error: IllegalStateException");
    }

    @Test
    void anUnexpectedErrorFinishingADashboardRunFailsTheJob() {
        var ws = new GitWorkspaceJobService.PreparedWorkspace(jobId, workspaceKey(), "https://github.com/org/repo", "agentic/wf-1/x");
        doThrow(new IllegalStateException("boom")).when(agentService).pollAgentRun("run-1", jobId);

        service.finishWorkflowRunAsync(ws, "run-1");

        assertThat(job.getStatus()).isEqualTo(GitWorkspaceJobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("Unexpected error: IllegalStateException");
        verify(gitMcp, never()).push(any(), any(), any());
    }

    @Test
    void aFinalCommitThatFailsFailsTheJobWithoutAPush() {
        var ws = new GitWorkspaceJobService.PreparedWorkspace(jobId, workspaceKey(), "https://github.com/org/repo", "agentic/wf-1/x");
        when(gitMcp.commit(any(), any(), any(), any(), any())).thenReturn(new GitMcpGateway.Result(false, "index locked", null, null));

        service.finishWorkflowRunAsync(ws, "run-1");

        assertThat(job.getStatus()).isEqualTo(GitWorkspaceJobStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo(GitWorkspaceJobErrorCode.COMMIT_FAILED);
        verify(gitMcp, never()).push(any(), any(), any());
    }

    @Test
    void aBranchThatCannotBeCreatedFailsTheJob() throws Exception {
        var spec = new SpecFile("spec-001", "build.md", "", "", "", false, "# Content", "");
        when(specFileService.resolveSpecFile("spec-001")).thenReturn(spec);
        when(gitMcp.createBranch(any(), any(), any(), any())).thenReturn(new GitMcpGateway.Result(false, "branch exists", null, null));

        service.runWorkflowAsync(jobId);

        assertThat(job.getStatus()).isEqualTo(GitWorkspaceJobStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo(GitWorkspaceJobErrorCode.BRANCH_CREATE_FAILED);
        verifyNoInteractions(agentService);
    }

    @Test
    void aRunThatDidNotStartEndsTheJob() {
        service.workflowRunNotStarted(jobId, "All agents were denied by policy");

        assertThat(job.getStatus()).isEqualTo(GitWorkspaceJobStatus.FAILED);
        assertThat(job.getErrorMessage()).contains("denied by policy");
    }

    // ── runWorkflowAsync: agent in same container ────────────────────────────

    @Test
    void runWorkflowAsyncRunsTheAgentsOnTheSameWorkspaceAsTheClone() throws Exception {
        var spec = new SpecFile("spec-001", "build.md", "", "", "", false, "# Content", "");
        when(specFileService.resolveSpecFile("spec-001")).thenReturn(spec);

        service.runWorkflowAsync(jobId);

        var order = inOrder(gitMcp, agentService);
        order.verify(gitMcp).createBranch(any(), any(), any(), eq(workspaceKey()));
        order.verify(agentService).runAgentsInsideContainer(eq(job), eq("cid-test"), any(), eq(project), eq(workspaceKey()),
                eq("agentic/test-project/20260703-123456-a1b2c3d4"));
        order.verify(gitMcp).commit(any(), any(), any(), any(), eq(workspaceKey()));
    }

    // ── runWorkflowAsync: unexpected errors ─────────────────────────────────

    @Test
    void runWorkflowAsyncHandlesUnexpectedException() throws Exception {
        when(specFileService.resolveSpecFile("spec-001")).thenThrow(new RuntimeException("boom"));

        service.runWorkflowAsync(jobId);

        assertThat(job.getErrorCode()).isEqualTo(GitWorkspaceJobErrorCode.CONTAINER_EXECUTION_FAILED);
        assertThat(job.getErrorMessage()).contains("RuntimeException");
    }

    @Test
    void runWorkflowAsyncSkipsWorkspaceCleanupWhenWorkspaceNeverCreated() throws Exception {
        when(projectRepo.findById(projectId)).thenReturn(Optional.empty());

        service.runWorkflowAsync(jobId);

        assertThat(job.getErrorCode()).isEqualTo(GitWorkspaceJobErrorCode.PROJECT_NOT_FOUND);
        verify(containerService).cleanupContainer(null);
    }

    @Test
    void runWorkflowAsyncDeletesRealFilesFromWorkspace() throws Exception {
        var spec = new SpecFile("spec-001", "build.md", "", "", "", false, "# Content", "");
        when(specFileService.resolveSpecFile("spec-001")).thenReturn(spec);
        doAnswer(inv -> {
            Path specDir = inv.getArgument(1);
            Files.createDirectories(specDir);
            Files.writeString(specDir.resolve("spec.md"), "# Content");
            return null;
        }).when(specFileService).writeSpecToWorkspace(eq(spec), any());

        service.runWorkflowAsync(jobId);

        Path expectedDir = Path.of(properties.getWorkspaceBasePath()).resolve("job-" + jobId);
        assertThat(expectedDir).doesNotExist();
    }

    @Test
    void runWorkflowAsyncCatchesWorkspaceDeletionFailure() throws Exception {
        var spec = new SpecFile("spec-001", "build.md", "", "", "", false, "# Content", "");
        when(specFileService.resolveSpecFile("spec-001")).thenReturn(spec);
        Path expectedDir = Path.of(properties.getWorkspaceBasePath()).resolve("job-" + jobId);
        doAnswer(inv -> {
            FileSystemUtils.deleteRecursively(expectedDir);
            return null;
        }).when(agentService).runAgentsInsideContainer(any(), any(), any(), any(), any(), any());

        service.runWorkflowAsync(jobId);

        verify(containerService).cleanupContainer("cid-test");
    }

    // ── addEvent ─────────────────────────────────────────────────────────────

    @Test
    void addEventCatchesRepositorySaveFailure() {
        when(eventRepo.save(any())).thenThrow(new RuntimeException("db down"));

        service.addEvent(jobId, "TEST_EVENT", "message");

        verify(eventRepo).save(any());
    }

    @Test
    void addEventPassesThroughNullMessage() {
        service.addEvent(jobId, "TEST_EVENT", null);

        verify(eventRepo).save(argThat(e -> ((GitWorkspaceJobEvent) e).getMessage() == null));
    }

    @Test
    void addEventTruncatesLongMessage() {
        String longMessage = "x".repeat(1000);

        service.addEvent(jobId, "TEST_EVENT", longMessage);

        verify(eventRepo).save(argThat(e -> ((GitWorkspaceJobEvent) e).getMessage().length() == 990));
    }

    // ── findById / findEvents ────────────────────────────────────────────────

    @Test
    void findByIdReturnsDto() {
        var dto = service.findById(jobId);
        assertThat(dto.id()).isEqualTo(jobId);
        assertThat(dto.projectId()).isEqualTo(projectId);
    }

    @Test
    void findByIdThrowsNotFoundForUnknownJob() {
        when(jobRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void findEventsReturnsListWhenJobExists() {
        var event = new GitWorkspaceJobEvent(jobId, "JOB_CREATED", "Job created");
        when(eventRepo.findAllByJobIdOrderByCreatedAtAsc(jobId)).thenReturn(List.of(event));

        List<GitWorkspaceJobEventDto> events = service.findEvents(jobId);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo("JOB_CREATED");
    }

    @Test
    void findEventsThrowsNotFoundWhenJobMissing() {
        UUID unknownJobId = UUID.randomUUID();
        when(jobRepo.existsById(unknownJobId)).thenReturn(false);

        assertThatThrownBy(() -> service.findEvents(unknownJobId))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ── helper: credentials masked in logs ──────────────────────────────────

    @Test
    void maskUrlIsCalledWithGitUrlWhenCloning() throws Exception {
        var spec = new SpecFile("spec-001", "build.md", "", "", "", false, "# Content", "");
        when(specFileService.resolveSpecFile("spec-001")).thenReturn(spec);

        service.runWorkflowAsync(jobId);

        verify(gitOps, atLeastOnce()).maskUrl(project.getGitUrl());
    }
}