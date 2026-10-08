package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.*;
import nl.metafactory.aicontrol.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class GitWorkspaceJobService {

    private static final Logger log = LoggerFactory.getLogger(GitWorkspaceJobService.class);

    private final GitWorkspaceJobRepository jobRepo;
    private final GitWorkspaceJobEventRepository eventRepo;
    private final ProjectRepository projectRepo;
    private final ProjectGitCredentialRepository credRepo;
    private final BranchNameGenerator branchNameGenerator;
    private final SpecFileService specFileService;
    private final GitOperationService gitOps;
    private final WorkflowContainerService containerService;
    private final ContainerizedAgentExecutionService agentService;
    private final AgenticWorkflowProperties properties;

    public GitWorkspaceJobService(GitWorkspaceJobRepository jobRepo,
                                   GitWorkspaceJobEventRepository eventRepo,
                                   ProjectRepository projectRepo,
                                   ProjectGitCredentialRepository credRepo,
                                   BranchNameGenerator branchNameGenerator,
                                   SpecFileService specFileService,
                                   GitOperationService gitOps,
                                   WorkflowContainerService containerService,
                                   ContainerizedAgentExecutionService agentService,
                                   AgenticWorkflowProperties properties) {
        this.jobRepo = jobRepo;
        this.eventRepo = eventRepo;
        this.projectRepo = projectRepo;
        this.credRepo = credRepo;
        this.branchNameGenerator = branchNameGenerator;
        this.specFileService = specFileService;
        this.gitOps = gitOps;
        this.containerService = containerService;
        this.agentService = agentService;
        this.properties = properties;
    }

    @Transactional
    public GitWorkspaceJobDto createJob(UUID projectId, String specFileRef, String startedBy) {
        Project project = validateSelectedProject(projectId);

        var job = new GitWorkspaceJob();
        job.setProjectId(projectId);
        job.setSpecFileRef(specFileRef);
        job.setStatus(GitWorkspaceJobStatus.CREATED);
        job.setBaseBranch(resolveBaseBranch(project));
        job.setStartedBy(startedBy);
        job.setStartedAt(Instant.now());

        job = jobRepo.save(job);
        addEvent(job.getId(), "JOB_CREATED", "Workflow job created for project " + project.getName());
        return toDto(job);
    }

    @Async("workflowExecutor")
    public void runWorkflowAsync(UUID jobId) {
        GitWorkspaceJob job = loadJob(jobId);
        Path workspaceDir = null;
        String containerId = null;

        try {
            Project project = validateSelectedProject(job.getProjectId());
            ProjectGitCredential credential = loadCredential(job.getProjectId());

            // 1. Prepare workspace
            workspaceDir = prepareWorkspace(job);

            // 2. Retrieve and validate spec
            SpecFile spec = specFileService.resolveSpecFile(job.getSpecFileRef());
            specFileService.validateSpecFile(spec);
            Path specDir = workspaceDir.resolve("spec");
            specFileService.writeSpecToWorkspace(spec, specDir);
            updateStatus(jobId, GitWorkspaceJobStatus.SPEC_READY,
                    "Spec file available: " + spec.fileName());

            // 3. Container lifecycle
            containerId = containerService.createWorkflowContainer(job.getId(), job.getProjectId());
            saveContainerId(jobId, containerId);
            updateStatus(jobId, GitWorkspaceJobStatus.CONTAINER_CREATED,
                    "Workflow container created");

            containerService.startContainer(containerId);
            updateStatus(jobId, GitWorkspaceJobStatus.CONTAINER_STARTED,
                    "Workflow container started");

            // 4. Clone
            updateStatus(jobId, GitWorkspaceJobStatus.CLONING,
                    "Cloning repository: " + gitOps.maskUrl(project.getGitUrl()));
            Path repoDir = workspaceDir.resolve("repo");
            gitOps.cloneDefaultBranch(project.getGitUrl(), job.getBaseBranch(), repoDir, credential);

            // 5. Create branch
            String branch = branchNameGenerator.generate(project, jobId);
            saveBranch(jobId, branch);
            gitOps.checkoutNewBranch(repoDir, branch);
            updateStatus(jobId, GitWorkspaceJobStatus.BRANCH_CREATED,
                    "Branch created: " + branch);

            // 6. Run agents
            updateStatus(jobId, GitWorkspaceJobStatus.AGENTS_RUNNING,
                    "Starting agents inside workflow container");
            Path outputDir = workspaceDir.resolve("output");
            Files.createDirectories(outputDir);
            agentService.runAgentsInsideContainer(job, containerId, repoDir, specDir, outputDir, project);

            // 7. Detect changes
            if (!gitOps.hasChanges(repoDir)) {
                updateStatusNoChanges(jobId);
                return;
            }
            updateStatus(jobId, GitWorkspaceJobStatus.CHANGES_DETECTED,
                    "Changes detected in workspace");

            // 8. Commit
            updateStatus(jobId, GitWorkspaceJobStatus.COMMITTING, "Committing changes");
            gitOps.stageAll(repoDir);
            String commitMsg = buildCommitMessage(project, job, spec);
            String commitHash = gitOps.commit(repoDir, commitMsg,
                    "Agentic Workflow", "agentic@metafactory.nl");
            saveCommitHash(jobId, commitHash);

            // 9. Push
            updateStatus(jobId, GitWorkspaceJobStatus.PUSHING,
                    "Pushing branch to remote: " + branch);
            gitOps.pushBranch(repoDir, "origin", branch, credential);
            updateStatus(jobId, GitWorkspaceJobStatus.PUSHED,
                    "Branch pushed successfully: " + branch);

            // 10. Completed
            markCompleted(jobId);
            log.info("Workflow job {} completed. Branch: {}, commit: {}", jobId, branch, commitHash);

        } catch (GitWorkspaceException e) {
            log.error("Workflow job {} failed [{}]: {}", jobId, e.getErrorCode(), e.getMessage());
            markFailed(jobId, e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in workflow job {}", jobId, e);
            markFailed(jobId, GitWorkspaceJobErrorCode.CONTAINER_EXECUTION_FAILED,
                    "Unexpected error: " + e.getClass().getSimpleName());
        } finally {
            updateStatus(jobId, GitWorkspaceJobStatus.CLEANING_UP, "Cleanup started");
            containerService.cleanupContainer(containerId);
            deleteWorkspace(workspaceDir, jobId);
        }
    }

    @Transactional(readOnly = true)
    public GitWorkspaceJobDto findById(UUID jobId) {
        return toDto(loadJob(jobId));
    }

    @Transactional(readOnly = true)
    public List<GitWorkspaceJobEventDto> findEvents(UUID jobId) {
        if (!jobRepo.existsById(jobId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId);
        }
        return eventRepo.findAllByJobIdOrderByCreatedAtAsc(jobId).stream()
                .map(this::toEventDto)
                .toList();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    Project validateSelectedProject(UUID projectId) {
        if (projectId == null) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.NO_SELECTED_PROJECT,
                    "No project selected");
        }
        Project project = projectRepo.findById(projectId)
                .orElseThrow(() -> new GitWorkspaceException(
                        GitWorkspaceJobErrorCode.PROJECT_NOT_FOUND,
                        "Project not found: " + projectId));
        if (project.getActive() != 1) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.PROJECT_INACTIVE,
                    "Project is inactive: " + project.getName());
        }
        if (project.getGitUrl() == null || project.getGitUrl().isBlank()) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.GIT_URL_MISSING,
                    "Project has no Git URL: " + project.getName());
        }
        return project;
    }

    private ProjectGitCredential loadCredential(UUID projectId) {
        return credRepo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(projectId, (short) 1)
                .orElse(null);
    }

    private String resolveBaseBranch(Project project) {
        return project.getDefaultBranch() != null && !project.getDefaultBranch().isBlank()
                ? project.getDefaultBranch()
                : properties.getDefaultBaseBranch();
    }

    private Path prepareWorkspace(GitWorkspaceJob job) throws IOException {
        Path base = Path.of(properties.getWorkspaceBasePath());
        Files.createDirectories(base);
        Path dir = base.resolve("job-" + job.getId());
        Files.createDirectories(dir);
        saveWorkspacePath(job.getId(), dir.toString());
        log.info("Workspace created: {}", dir);
        return dir;
    }

    private String buildCommitMessage(Project project, GitWorkspaceJob job, SpecFile spec) {
        return "Apply agentic workflow spec for " + project.getName() + "\n\n" +
               "Job ID:     " + job.getId() + "\n" +
               "Spec:       " + spec.fileName() + "\n" +
               "Branch:     " + job.getWorkingBranch() + "\n" +
               "Timestamp:  " + Instant.now() + "\n";
    }

    private void deleteWorkspace(Path dir, UUID jobId) {
        if (dir == null) return;
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
            log.info("Workspace deleted for job {}", jobId);
        } catch (Exception e) {
            log.warn("Failed to delete workspace for job {}: {}", jobId, e.getMessage());
        }
    }

    private GitWorkspaceJob loadJob(UUID jobId) {
        return jobRepo.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Job not found: " + jobId));
    }

    @Transactional
    void updateStatus(UUID jobId, GitWorkspaceJobStatus status, String message) {
        jobRepo.findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            jobRepo.save(job);
        });
        addEvent(jobId, status.name(), message);
    }

    @Transactional
    void saveContainerId(UUID jobId, String containerId) {
        jobRepo.findById(jobId).ifPresent(job -> {
            job.setContainerId(containerId);
            jobRepo.save(job);
        });
    }

    @Transactional
    void saveBranch(UUID jobId, String branch) {
        jobRepo.findById(jobId).ifPresent(job -> {
            job.setWorkingBranch(branch);
            jobRepo.save(job);
        });
    }

    @Transactional
    void saveWorkspacePath(UUID jobId, String path) {
        jobRepo.findById(jobId).ifPresent(job -> {
            job.setWorkspacePath(path);
            jobRepo.save(job);
        });
    }

    @Transactional
    void saveCommitHash(UUID jobId, String hash) {
        jobRepo.findById(jobId).ifPresent(job -> {
            job.setCommitHash(hash);
            jobRepo.save(job);
        });
    }

    @Transactional
    void markCompleted(UUID jobId) {
        jobRepo.findById(jobId).ifPresent(job -> {
            job.setStatus(GitWorkspaceJobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            jobRepo.save(job);
        });
        addEvent(jobId, "COMPLETED", "Workflow completed successfully");
    }

    @Transactional
    void updateStatusNoChanges(UUID jobId) {
        jobRepo.findById(jobId).ifPresent(job -> {
            job.setStatus(GitWorkspaceJobStatus.NO_CHANGES);
            job.setErrorCode(GitWorkspaceJobErrorCode.NO_CHANGES);
            job.setCompletedAt(Instant.now());
            jobRepo.save(job);
        });
        addEvent(jobId, GitWorkspaceJobStatus.NO_CHANGES.name(),
                "No changes detected — no commit created");
        log.info("Job {} has no changes", jobId);
    }

    @Transactional
    void markFailed(UUID jobId, GitWorkspaceJobErrorCode code, String message) {
        String safeMessage = sanitizeMessage(message);
        jobRepo.findById(jobId).ifPresent(job -> {
            job.setStatus(GitWorkspaceJobStatus.FAILED);
            job.setErrorCode(code);
            job.setErrorMessage(safeMessage);
            job.setCompletedAt(Instant.now());
            jobRepo.save(job);
        });
        addEvent(jobId, "FAILED", code.name() + ": " + safeMessage);
    }

    @Transactional
    void addEvent(UUID jobId, String type, String message) {
        try {
            eventRepo.save(new GitWorkspaceJobEvent(jobId, type, sanitizeMessage(message)));
        } catch (Exception e) {
            log.warn("Failed to save event for job {}: {}", jobId, e.getMessage());
        }
    }

    private String sanitizeMessage(String message) {
        if (message == null) return null;
        return message.length() > 990 ? message.substring(0, 990) : message;
    }

    GitWorkspaceJobDto toDto(GitWorkspaceJob job) {
        return new GitWorkspaceJobDto(
                job.getId(), job.getProjectId(), job.getSpecFileRef(),
                job.getStatus(), job.getBaseBranch(), job.getWorkingBranch(),
                job.getCommitHash(), job.getErrorCode(), job.getErrorMessage(),
                job.getStartedBy(), job.getStartedAt(), job.getCompletedAt(),
                job.getCreatedAt(), job.getUpdatedAt());
    }

    GitWorkspaceJobEventDto toEventDto(GitWorkspaceJobEvent event) {
        return new GitWorkspaceJobEventDto(
                event.getId(), event.getJobId(), event.getEventType(),
                event.getMessage(), event.getCreatedAt());
    }
}
