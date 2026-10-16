package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.*;
import nl.metafactory.aicontrol.repository.*;
import nl.metafactory.common.mcp.GitMcpGateway;
import nl.metafactory.common.mcp.LiteLlmMcpToolClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class GitWorkspaceJobService {

    private static final Logger log = LoggerFactory.getLogger(GitWorkspaceJobService.class);

    private final GitWorkspaceJobRepository jobRepo;
    private final GitWorkspaceJobEventRepository eventRepo;
    private final ProjectGitResolver projectGit;
    private final BranchNameGenerator branchNameGenerator;
    private final SpecFileService specFileService;
    private final GitOperationService gitOps;
    private final WorkflowContainerService containerService;
    private final ContainerizedAgentExecutionService agentService;
    private final AgenticWorkflowProperties properties;
    private final GitMcpGateway gitMcp;

    public GitWorkspaceJobService(GitWorkspaceJobRepository jobRepo,
                                   GitWorkspaceJobEventRepository eventRepo,
                                   ProjectGitResolver projectGit,
                                   BranchNameGenerator branchNameGenerator,
                                   SpecFileService specFileService,
                                   GitOperationService gitOps,
                                   WorkflowContainerService containerService,
                                   ContainerizedAgentExecutionService agentService,
                                   AgenticWorkflowProperties properties,
                                   GitMcpGateway gitMcp) {
        this.jobRepo = jobRepo;
        this.eventRepo = eventRepo;
        this.projectGit = projectGit;
        this.branchNameGenerator = branchNameGenerator;
        this.specFileService = specFileService;
        this.gitOps = gitOps;
        this.containerService = containerService;
        this.agentService = agentService;
        this.properties = properties;
        this.gitMcp = gitMcp;
    }

    @Transactional
    public GitWorkspaceJobDto createJob(UUID projectId, String specFileRef, String workflowId, String startedBy) {
        Project project = projectGit.validateProject(projectId);

        var job = new GitWorkspaceJob();
        job.setProjectId(projectId);
        job.setSpecFileRef(specFileRef);
        job.setWorkflowId(workflowId);
        job.setStatus(GitWorkspaceJobStatus.CREATED);
        job.setBaseBranch(projectGit.resolveBaseBranch(project));
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
            Project project = projectGit.validateProject(job.getProjectId());

            // 1. Prepare workspace
            workspaceDir = prepareWorkspace(job);

            // 2. Retrieve and validate spec
            SpecFile spec = specFileService.resolveSpecFile(job.getSpecFileRef());
            specFileService.validateSpecFile(spec);
            Path specDir = workspaceDir.resolve("spec");
            specFileService.writeSpecToWorkspace(spec, specDir);
            updateStatus(jobId, GitWorkspaceJobStatus.SPEC_READY,
                    "Spec file available: " + spec.fileName());

            // 3. Container lifecycle (the repository itself lives in the git MCP server's workspace)
            containerId = containerService.createWorkflowContainer(job.getId(), job.getProjectId());
            saveContainerId(jobId, containerId);
            updateStatus(jobId, GitWorkspaceJobStatus.CONTAINER_CREATED,
                    "Workflow container created");

            containerService.startContainer(containerId);
            updateStatus(jobId, GitWorkspaceJobStatus.CONTAINER_STARTED,
                    "Workflow container started");

            // 4. Clone + branch — in the job's workspace of the git MCP server, through LiteLLM. The
            //    git MCP server pulls the job's credentials from ai-control itself (never an argument).
            String workspaceKey = GitWorkspaceCredentialService.workspaceKey(jobId);
            String branch = branchNameGenerator.generate(project, jobId);
            saveBranch(jobId, branch);
            updateStatus(jobId, GitWorkspaceJobStatus.CLONING,
                    "Cloning repository in the git MCP workspace: " + gitOps.maskUrl(project.getGitUrl()));
            GitMcpGateway.Result branched = git(GitWorkspaceJobErrorCode.GIT_CLONE_FAILED,
                    () -> gitMcp.createBranch(project.getGitUrl(), job.getBaseBranch(), branch, workspaceKey));
            if (!branched.success()) {
                throw new GitWorkspaceException(GitWorkspaceJobErrorCode.BRANCH_CREATE_FAILED, branched.message());
            }
            updateStatus(jobId, GitWorkspaceJobStatus.BRANCH_CREATED,
                    "Branch created: " + branch);

            // 5. Run agents — the workflow's agents work in the same workspace
            updateStatus(jobId, GitWorkspaceJobStatus.AGENTS_RUNNING,
                    "Starting the workflow's agents on the job workspace");
            agentService.runAgentsInsideContainer(job, containerId, specDir, project, workspaceKey, branch);

            // 6–7. Final commit + push (the run already pushed a checkpoint after every agent)
            publish(jobId, project.getGitUrl(), branch, workspaceKey, buildCommitMessage(project, job, spec.fileName()));

        } catch (GitWorkspaceException e) {
            log.error("Workflow job {} failed [{}]: {}", jobId, e.getErrorCode(), e.getMessage());
            markFailed(jobId, e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in workflow job {}", jobId, e);
            markFailed(jobId, GitWorkspaceJobErrorCode.CONTAINER_EXECUTION_FAILED,
                    "Unexpected error: " + e.getClass().getSimpleName());
        } finally {
            // Recorded as an event only: the job's terminal status (COMPLETED / FAILED / NO_CHANGES) must
            // survive cleanup — it also ends the job workspace's access to the project credentials.
            addEvent(jobId, GitWorkspaceJobStatus.CLEANING_UP.name(), "Cleanup started");
            containerService.cleanupContainer(containerId);
            deleteWorkspace(workspaceDir, jobId);
        }
    }

    // ── Workflow runs started from the dashboard on a project ────────────────────

    /** A git workspace prepared for one workflow run: clone of the project repository, run branch checked out. */
    public record PreparedWorkspace(UUID jobId, String workspaceKey, String repositoryUrl, String branch) {
    }

    /**
     * Prepares the git workspace of a workflow run started on a project (synchronously, before the run
     * starts): a job record, a clone of the project repository in the git MCP server (through LiteLLM;
     * the server pulls the credentials from ai-control while the job is active) and a fresh branch named
     * after the workflow and the job. A failure marks the job FAILED and is thrown.
     */
    public PreparedWorkspace prepareWorkflowRunWorkspace(UUID projectId, String workflowId, String specRef,
                                                        String startedBy) {
        Project project = projectGit.validateProject(projectId);
        GitWorkspaceJobDto created = createJob(projectId, specRef, workflowId, startedBy);
        UUID jobId = created.id();
        String workspaceKey = GitWorkspaceCredentialService.workspaceKey(jobId);
        String branch = branchNameGenerator.forWorkflowRun(workflowId, jobId);
        try {
            saveBranch(jobId, branch);
            updateStatus(jobId, GitWorkspaceJobStatus.CLONING,
                    "Cloning repository in the git MCP workspace: " + gitOps.maskUrl(project.getGitUrl()));
            GitMcpGateway.Result branched = git(GitWorkspaceJobErrorCode.GIT_CLONE_FAILED,
                    () -> gitMcp.createBranch(project.getGitUrl(), created.baseBranch(), branch, workspaceKey));
            if (!branched.success()) {
                throw new GitWorkspaceException(GitWorkspaceJobErrorCode.BRANCH_CREATE_FAILED, branched.message());
            }
            updateStatus(jobId, GitWorkspaceJobStatus.BRANCH_CREATED, "Branch created: " + branch);
            return new PreparedWorkspace(jobId, workspaceKey, project.getGitUrl(), branch);
        } catch (GitWorkspaceException e) {
            markFailed(jobId, e.getErrorCode(), e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            markFailed(jobId, GitWorkspaceJobErrorCode.GIT_CLONE_FAILED, "Unexpected error: " + e.getClass().getSimpleName());
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.GIT_CLONE_FAILED,
                    "The project repository could not be prepared");
        }
    }

    /** The prepared workspace's run did not start (blocked, refused): the job ends, and so does its credential access. */
    public void workflowRunNotStarted(UUID jobId, String reason) {
        markFailed(jobId, GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED,
                "Workflow run not started" + (reason == null || reason.isBlank() ? "" : ": " + reason));
    }

    /**
     * Follows a started run on its prepared workspace until it ends, then makes the final commit and
     * push of the run branch (the run itself pushes a checkpoint after every agent). A run that does not
     * complete fails the job; the checkpoints already pushed stay on the branch.
     */
    @Async("workflowExecutor")
    public void finishWorkflowRunAsync(PreparedWorkspace workspace, String runId) {
        UUID jobId = workspace.jobId();
        try {
            updateStatus(jobId, GitWorkspaceJobStatus.AGENTS_RUNNING, "Workflow run started: " + runId);
            agentService.pollAgentRun(runId, jobId);
            GitWorkspaceJob job = loadJob(jobId);
            Project project = projectGit.validateProject(job.getProjectId());
            publish(jobId, workspace.repositoryUrl(), workspace.branch(), workspace.workspaceKey(),
                    buildCommitMessage(project, job, job.getSpecFileRef()));
        } catch (GitWorkspaceException e) {
            log.error("Workflow run {} of job {} failed [{}]: {}", runId, jobId, e.getErrorCode(), e.getMessage());
            markFailed(jobId, e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error finishing workflow run {} of job {}", runId, jobId, e);
            markFailed(jobId, GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED,
                    "Unexpected error: " + e.getClass().getSimpleName());
        }
    }

    /**
     * Final commit and push of the run branch. "Nothing to commit" is normal: every agent's work was
     * already committed and pushed by its checkpoint; the branch is pushed again regardless, so a
     * checkpoint push that failed is retried here.
     */
    private void publish(UUID jobId, String repositoryUrl, String branch, String workspaceKey, String commitMsg) {
        updateStatus(jobId, GitWorkspaceJobStatus.COMMITTING, "Committing remaining changes");
        GitMcpGateway.Result committed = git(GitWorkspaceJobErrorCode.COMMIT_FAILED,
                () -> gitMcp.commit(repositoryUrl, commitMsg, "Agentic Workflow", "agentic@metafactory.nl", workspaceKey));
        String commitHash = null;
        if (committed.success()) {
            commitHash = committed.commitHash();
            saveCommitHash(jobId, commitHash);
            updateStatus(jobId, GitWorkspaceJobStatus.CHANGES_DETECTED, "Changes committed: " + commitHash);
        } else if (committed.message() != null && committed.message().startsWith("Nothing to commit")) {
            addEvent(jobId, "NOTHING_LEFT_TO_COMMIT", "All changes were already committed by the run's checkpoints");
        } else {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.COMMIT_FAILED, committed.message());
        }

        updateStatus(jobId, GitWorkspaceJobStatus.PUSHING, "Pushing branch to remote: " + branch);
        GitMcpGateway.Result pushed = git(GitWorkspaceJobErrorCode.PUSH_FAILED,
                () -> gitMcp.push(repositoryUrl, branch, workspaceKey));
        if (!pushed.success()) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.PUSH_FAILED, pushed.message());
        }
        updateStatus(jobId, GitWorkspaceJobStatus.PUSHED, "Branch pushed successfully: " + branch);
        markCompleted(jobId);
        log.info("Workflow job {} completed. Branch: {}, commit: {}", jobId, branch, commitHash);
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

    /** Runs a git MCP call; an unreachable gateway or MCP server becomes a job failure with {@code code}. */
    private static GitMcpGateway.Result git(GitWorkspaceJobErrorCode code,
                                            java.util.function.Supplier<GitMcpGateway.Result> call) {
        try {
            return call.get();
        } catch (LiteLlmMcpToolClient.McpCallException e) {
            throw new GitWorkspaceException(code, e.getMessage());
        }
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

    private String buildCommitMessage(Project project, GitWorkspaceJob job, String spec) {
        return "Apply agentic workflow " + job.getWorkflowId() + " for " + project.getName() + "\n\n" +
               "Job ID:     " + job.getId() + "\n" +
               "Spec:       " + spec + "\n" +
               "Branch:     " + job.getWorkingBranch() + "\n" +
               "Timestamp:  " + Instant.now() + "\n";
    }

    private void deleteWorkspace(Path dir, UUID jobId) {
        if (dir == null) return;
        try {
            WorkspaceFiles.deleteTree(dir);
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
                job.getCreatedAt(), job.getUpdatedAt(), job.getWorkflowId());
    }

    GitWorkspaceJobEventDto toEventDto(GitWorkspaceJobEvent event) {
        return new GitWorkspaceJobEventDto(
                event.getId(), event.getJobId(), event.getEventType(),
                event.getMessage(), event.getCreatedAt());
    }
}
