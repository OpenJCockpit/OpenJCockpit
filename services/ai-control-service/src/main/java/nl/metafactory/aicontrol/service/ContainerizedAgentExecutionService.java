package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.client.AgentRunDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.WorkflowStartInputDto;
import nl.metafactory.aicontrol.client.WorkflowStartResponseDto;
import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.GitWorkspaceJob;
import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.repository.GitWorkspaceJobEventRepository;
import nl.metafactory.common.oauth.ClientCredentialsTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Executes the agents of a git-workspace job: the job's workflow is started in embabel-agent-service (as
 * ai-control's service identity), bound through a single-use ticket to the job's workspace in the git MCP
 * server. The workflow's agents read and write the cloned repository there through LiteLLM, and after
 * each agent embabel commits and pushes a checkpoint (including that agent's full output under
 * agent-output/&lt;run-id&gt;/). The job's final commit and push pick up anything left.
 */
@Service
public class ContainerizedAgentExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ContainerizedAgentExecutionService.class);
    private static final long POLL_INTERVAL_MS = 2_000;
    // RUN_STATE_LOST: the agent service no longer knows the run (e.g. restarted) — it will never finish.
    private static final List<String> TERMINAL_STATUSES = List.of("COMPLETED", "FAILED", "CANCELLED", "DENIED", "RUN_STATE_LOST");

    private final EmbabelAgentClient agentClient;
    private final GitWorkspaceJobEventRepository eventRepo;
    private final AgenticWorkflowProperties properties;
    private final ClientCredentialsTokens serviceTokens;

    public ContainerizedAgentExecutionService(EmbabelAgentClient agentClient,
                                              GitWorkspaceJobEventRepository eventRepo,
                                              AgenticWorkflowProperties properties,
                                              @Qualifier("jobServiceTokens") ClientCredentialsTokens serviceTokens) {
        this.agentClient = agentClient;
        this.eventRepo = eventRepo;
        this.properties = properties;
        this.serviceTokens = serviceTokens;
    }

    public void runAgentsInsideContainer(GitWorkspaceJob job, String containerId, Path specPath,
                                         Project project, String workspaceKey, String branch) {
        addEvent(job.getId(), "AGENT_START", "Agent execution started for job " + job.getId());
        if (job.getWorkflowId() == null || job.getWorkflowId().isBlank()) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.WORKFLOW_NOT_FOUND,
                    "Job has no workflow; start jobs with a workflowId");
        }
        String authorization = serviceAuthorization();

        // The job runs the workflow's linked catalog agents through the normal workflow-run path
        // (policy, gates, orbs apply); the spec file content is the workflow input. The run is bound
        // to the job's git workspace (ticket for ai-control itself), so the agents' git tools work in
        // the cloned repository and the checkpoints push the job branch.
        String ticket = issueTicket(authorization, new EmbabelAgentClient.GitWorkspaceTicketRequest(
                workspaceKey, project.getGitUrl(), branch, job.getWorkflowId(), null));
        var input = new WorkflowStartInputDto(null, readSpecContent(specPath), project.getGitUrl(),
                project.getId() != null ? project.getId().toString() : null, null, null, job.getBaseBranch(),
                ticket);
        WorkflowStartResponseDto start = agentClient.startWorkflowAs(authorization, job.getWorkflowId(), input);
        if (start == null || start.executionId() == null) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED,
                    "Workflow " + job.getWorkflowId() + " could not be started"
                            + (start != null && start.message() != null ? ": " + start.message() : ""));
        }

        addEvent(job.getId(), "AGENT_RUN_STARTED", "Workflow " + job.getWorkflowId() + " run started: " + start.executionId());
        pollAgentRun(start.executionId(), job.getId());
        addEvent(job.getId(), "AGENT_COMPLETE", "Agent execution completed");
    }

    /** A ticket from embabel for a prepared workspace; any failure is an agent-execution failure. */
    String issueTicket(String authorization, EmbabelAgentClient.GitWorkspaceTicketRequest request) {
        try {
            return agentClient.issueGitWorkspaceTicketAs(authorization, request);
        } catch (RuntimeException e) {
            log.warn("git workspace ticket for {} could not be issued: {}", request.workspaceKey(), e.getClass().getSimpleName());
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED,
                    "The run could not be bound to its git workspace");
        }
    }

    String serviceAuthorization() {
        try {
            return serviceTokens.authorization().orElseThrow(() -> new GitWorkspaceException(
                    GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED,
                    "ai-control has no service identity configured for git-workspace jobs"));
        } catch (GitWorkspaceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED,
                    "ai-control service identity token could not be obtained");
        }
    }

    void pollAgentRun(String runId, UUID jobId) {
        long deadline = System.currentTimeMillis() + (long) properties.getAgentTimeoutSeconds() * 1000;
        while (System.currentTimeMillis() < deadline) {
            AgentRunDto run = agentClient.getLatestRunAs(serviceAuthorization(), runId).orElse(null);
            if (run == null) {
                throw new GitWorkspaceException(GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED,
                        "Agent run disappeared: " + runId);
            }
            if (TERMINAL_STATUSES.contains(run.status())) {
                if (!"COMPLETED".equals(run.status())) {
                    throw new GitWorkspaceException(GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED,
                            "Agent run ended with status: " + run.status());
                }
                return;
            }
            try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GitWorkspaceException(GitWorkspaceJobErrorCode.TIMEOUT, "Interrupted during polling");
            }
        }
        // The job gives up: stop the run too, so it does not keep changing a workspace nobody commits.
        try {
            agentClient.stopAgentRunAs(serviceAuthorization(), runId);
        } catch (RuntimeException e) {
            log.warn("Timed-out agent run {} could not be stopped: {}", runId, e.getClass().getSimpleName());
        }
        throw new GitWorkspaceException(GitWorkspaceJobErrorCode.TIMEOUT,
                "Agent timeout na " + properties.getAgentTimeoutSeconds() + "s");
    }

    private String readSpecContent(Path specPath) {
        try {
            Path specFile = specPath.resolve("spec.md");
            return Files.readString(specFile);
        } catch (Exception e) {
            log.warn("Spec file could not be read from {}: {}", specPath, e.getMessage());
            return "";
        }
    }

    private void addEvent(UUID jobId, String type, String message) {
        try {
            var event = new nl.metafactory.aicontrol.model.GitWorkspaceJobEvent(jobId, type, message);
            eventRepo.save(event);
        } catch (Exception e) {
            log.warn("Failed to save event: {}", e.getMessage());
        }
    }
}
