package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.WorkflowStartInputDto;
import nl.metafactory.aicontrol.client.WorkflowStartResponseDto;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import nl.metafactory.common.oauth.ClientCredentialsTokens;
import nl.metafactory.common.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

/**
 * Starts a dashboard workflow run on a project inside a git workspace shared by all of the run's agents:
 * <ol>
 *   <li>the project repository is cloned in the git MCP server and a branch named after the workflow and
 *       the run's job is created (synchronously, before any agent runs);</li>
 *   <li>embabel issues — to ai-control's service identity — a single-use ticket for that workspace, this
 *       workflow and the starting user;</li>
 *   <li>the run is started with the user's own token (policy and run attribution stay the user's) and the
 *       ticket, which binds it to the workspace;</li>
 *   <li>embabel commits and pushes a checkpoint after every agent; ai-control makes the final commit and
 *       push when the run ends.</li>
 * </ol>
 * Runs without a project (or with a project without repository) start as before, without a workspace.
 */
@Service
public class ProjectWorkflowRunService {

    private static final Logger log = LoggerFactory.getLogger(ProjectWorkflowRunService.class);
    private static final String UNKNOWN = "unknown";
    private static final int MAX_SPEC_REF = 500;

    private final ProjectRepository projects;
    private final GitWorkspaceJobService jobs;
    private final EmbabelAgentClient client;
    private final ClientCredentialsTokens serviceTokens;

    public ProjectWorkflowRunService(ProjectRepository projects, GitWorkspaceJobService jobs, EmbabelAgentClient client,
                                     @Qualifier("jobServiceTokens") ClientCredentialsTokens serviceTokens) {
        this.projects = projects;
        this.jobs = jobs;
        this.client = client;
        this.serviceTokens = serviceTokens;
    }

    /**
     * Starts {@code workflowId} on the input's project in a git workspace; empty when the input names no
     * project with a repository (the caller then starts a plain run). {@code selectedSpec} is the spec the
     * user picked (its name), recorded on the job.
     */
    public Optional<WorkflowStartResponseDto> startOnProjectWorkspace(String workflowId, String selectedSpec,
                                                                     WorkflowStartInputDto input) {
        Optional<Project> project = projectOf(input.projectId());
        if (project.isEmpty()) {
            return Optional.empty();
        }
        String subject = CurrentUser.subject();
        if (subject == null || subject.isBlank() || UNKNOWN.equals(subject)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A workflow run on a project needs a signed-in user");
        }
        String authorization = serviceAuthorization();

        GitWorkspaceJobService.PreparedWorkspace workspace;
        try {
            workspace = jobs.prepareWorkflowRunWorkspace(project.get().getId(), workflowId, specRef(workflowId, selectedSpec),
                    CurrentUser.username());
        } catch (GitWorkspaceException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The project repository could not be prepared: " + e.getMessage());
        }

        String ticket;
        try {
            ticket = client.issueGitWorkspaceTicketAs(authorization, new EmbabelAgentClient.GitWorkspaceTicketRequest(
                    workspace.workspaceKey(), workspace.repositoryUrl(), workspace.branch(), workflowId, subject));
        } catch (RuntimeException e) {
            log.warn("git workspace ticket for {} could not be issued: {}", workspace.workspaceKey(), e.getClass().getSimpleName());
            jobs.workflowRunNotStarted(workspace.jobId(), "the run could not be bound to its git workspace");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The run could not be bound to its git workspace");
        }

        WorkflowStartResponseDto response;
        try {
            response = client.startWorkflow(workflowId, input.withGitWorkspaceTicket(ticket));
        } catch (RuntimeException e) {
            jobs.workflowRunNotStarted(workspace.jobId(), e.getClass().getSimpleName());
            throw e;
        }
        if (response == null || response.executionId() == null) {
            jobs.workflowRunNotStarted(workspace.jobId(), response == null ? null : response.message());
            return Optional.ofNullable(response);
        }
        log.info("workflow.run.on-workspace workflowId={} runId={} job={} branch={}", workflowId, response.executionId(),
                workspace.jobId(), workspace.branch());
        jobs.finishWorkflowRunAsync(workspace, response.executionId());
        String message = (response.message() == null ? "Workflow started" : response.message())
                + " — working on branch " + workspace.branch();
        return Optional.of(new WorkflowStartResponseDto(response.workflowId(), response.executionId(), response.status(),
                response.startedAt(), message));
    }

    private Optional<Project> projectOf(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return Optional.empty();
        }
        try {
            return projects.findById(UUID.fromString(projectId))
                    .filter(p -> p.getGitUrl() != null && !p.getGitUrl().isBlank());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String serviceAuthorization() {
        try {
            return serviceTokens.authorization().orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Git workspaces are not configured: ai-control has no service identity"));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "ai-control's service identity token could not be obtained");
        }
    }

    /** The selected spec's name as the job's reference; the workflow when none (or a full spec text) was given. */
    static String specRef(String workflowId, String selectedSpec) {
        if (selectedSpec == null || selectedSpec.isBlank() || selectedSpec.contains("\n")
                || selectedSpec.length() > MAX_SPEC_REF) {
            return "workflow:" + workflowId;
        }
        return selectedSpec.trim();
    }
}
