package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.*;
import nl.metafactory.aicontrol.repository.GitWorkspaceJobEventRepository;
import nl.metafactory.aicontrol.repository.GitWorkspaceJobRepository;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.repository.ProjectRepository;
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

        properties = new AgenticWorkflowProperties();
        properties.setWorkspaceBasePath(tempDir.toString());
        properties.setDefaultBaseBranch("main");

        service = new GitWorkspaceJobService(jobRepo, eventRepo, projectRepo, credRepo,
                branchNameGenerator, specFileService, gitOps, containerService, agentService, properties);

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
        when(gitOps.hasChanges(any())).thenReturn(true);
        when(gitOps.commit(any(), any(), any(), any())).thenReturn("abc123commit");
        when(gitOps.maskUrl(any())).thenReturn("https://github.com/org/repo");
    }

    // ── createJob validation ────────────────────────────────────────────────

    @Test
    void createJobFailsWhenProjectIdNull() {
        assertThatThrownBy(() -> service.createJob(null, "spec", "user"))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.NO_SELECTED_PROJECT));
    }

    @Test
    void createJobFailsWhenProjectNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(projectRepo.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createJob(unknownId, "spec", "user"))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.PROJECT_NOT_FOUND));
    }

    @Test
    void createJobFailsWhenProjectInactive() {
        project.setActive((short) 0);

        assertThatThrownBy(() -> service.createJob(projectId, "spec", "user"))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.PROJECT_INACTIVE));
    }

    @Test
    void createJobFailsWhenGitUrlMissing() {
        project.setGitUrl(null);

        assertThatThrownBy(() -> service.createJob(projectId, "spec", "user"))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.GIT_URL_MISSING));
    }

    @Test
    void createJobFailsWhenGitUrlBlank() {
        project.setGitUrl("  ");

        assertThatThrownBy(() -> service.createJob(projectId, "spec", "user"))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.GIT_URL_MISSING));
    }

    @Test
    void createJobPersistsAndReturnsDto() {
        var dto = service.createJob(projectId, "spec-001", "alice");

        verify(jobRepo).save(any(GitWorkspaceJob.class));
        verify(eventRepo, atLeastOnce()).save(any());
    }

    @Test
    void createJobUsesDefaultBaseBranchWhenProjectHasNone() {
        project.setDefaultBranch(null);
        properties.setDefaultBaseBranch("develop");

        var dto = service.createJob(projectId, "spec-001", "alice");

        assertThat(dto.baseBranch()).isEqualTo("develop");
    }

    @Test
    void createJobUsesDefaultBaseBranchWhenProjectBranchBlank() {
        project.setDefaultBranch("   ");
        properties.setDefaultBaseBranch("develop");

        var dto = service.createJob(projectId, "spec-001", "alice");

        assertThat(dto.baseBranch()).isEqualTo("develop");
    }

    // ── runWorkflowAsync: happy path ────────────────────────────────────────

    @Test
    void runWorkflowAsyncHappyPathCleansUpWorkspace() throws Exception {
        var spec = new SpecFile("spec-001", "build.md", "", "", "", false, "# Content", "");
        when(specFileService.resolveSpecFile("spec-001")).thenReturn(spec);

        service.runWorkflowAsync(jobId);

        verify(containerService).cleanupContainer("cid-test");
        verify(gitOps).pushBranch(any(), eq("origin"),
                eq("agentic/test-project/20260703-123456-a1b2c3d4"), any());
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
        doThrow(new GitWorkspaceException(GitWorkspaceJobErrorCode.GIT_CLONE_FAILED, "clone error"))
                .when(gitOps).cloneDefaultBranch(any(), any(), any(), any());

        service.runWorkflowAsync(jobId);

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
        doThrow(new GitWorkspaceException(GitWorkspaceJobErrorCode.PUSH_FAILED, "push error"))
                .when(gitOps).pushBranch(any(), any(), any(), any());

        service.runWorkflowAsync(jobId);

        verify(containerService).cleanupContainer("cid-test");
        Path expectedDir = Path.of(properties.getWorkspaceBasePath()).resolve("job-" + jobId);
        assertThat(expectedDir).doesNotExist();
    }

    // ── runWorkflowAsync: no changes ────────────────────────────────────────

    @Test
    void runWorkflowAsyncSetsNoChangesStatusWhenNoDiff() throws Exception {
        var spec = new SpecFile("spec-001", "build.md", "", "", "", false, "# Content", "");
        when(specFileService.resolveSpecFile("spec-001")).thenReturn(spec);
        when(gitOps.hasChanges(any())).thenReturn(false);

        service.runWorkflowAsync(jobId);

        verify(gitOps, never()).stageAll(any());
        verify(gitOps, never()).pushBranch(any(), any(), any(), any());
        verify(containerService).cleanupContainer("cid-test");
    }

    // ── runWorkflowAsync: agent in same container ────────────────────────────

    @Test
    void runWorkflowAsyncRunsAgentInsideSameContainerAsClone() throws Exception {
        var spec = new SpecFile("spec-001", "build.md", "", "", "", false, "# Content", "");
        when(specFileService.resolveSpecFile("spec-001")).thenReturn(spec);

        service.runWorkflowAsync(jobId);

        verify(agentService).runAgentsInsideContainer(
                eq(job),
                eq("cid-test"),
                any(),
                any(),
                any(),
                eq(project));
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