package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.client.AgentDefinitionDto;
import nl.metafactory.aicontrol.client.AgentRunDto;
import nl.metafactory.aicontrol.client.AgentRunRequestDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.GitWorkspaceJob;
import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.repository.GitWorkspaceJobEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Executes agents within the context of a workflow job.
 *
 * Agents run in embabel-agent-service (existing). The repo path is
 * passed as repositoryUrl so that agents work on the correct workspace.
 * In Docker environments a shared volume is needed so that embabel-agent-service
 * can reach the same working paths.
 *
 * Agents per job are executed sequentially to avoid race conditions.
 */
@Service
public class ContainerizedAgentExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ContainerizedAgentExecutionService.class);
    private static final long POLL_INTERVAL_MS = 2_000;
    private static final List<String> TERMINAL_STATUSES = List.of("COMPLETED", "FAILED", "CANCELLED");

    private final EmbabelAgentClient agentClient;
    private final GitWorkspaceJobEventRepository eventRepo;
    private final AgenticWorkflowProperties properties;

    public ContainerizedAgentExecutionService(EmbabelAgentClient agentClient,
                                              GitWorkspaceJobEventRepository eventRepo,
                                              AgenticWorkflowProperties properties) {
        this.agentClient = agentClient;
        this.eventRepo = eventRepo;
        this.properties = properties;
    }

    public void runAgentsInsideContainer(GitWorkspaceJob job, String containerId,
                                         Path repoPath, Path specPath, Path outputPath,
                                         Project project) {
        addEvent(job.getId(), "AGENT_START", "Agent execution started for job " + job.getId());

        List<AgentDefinitionDto> defs = agentClient.getAgentDefinitions();
        List<String> agentIds = defs.stream()
                .sorted(Comparator.comparingInt(AgentDefinitionDto::sequenceOrder))
                .map(AgentDefinitionDto::id)
                .toList();

        String specContent = readSpecContent(specPath);
        String customerId = project.getCustomerId() != null
                ? project.getCustomerId() : project.getId().toString();

        AgentRunDto run = agentClient.startAgentRun(new AgentRunRequestDto(
                customerId,
                specContent,
                agentIds,
                "agentic-workflow-job-" + job.getId(),
                repoPath.toString()
        ));

        if (run == null) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.AGENT_EXECUTION_FAILED,
                    "Agent run could not be started");
        }

        addEvent(job.getId(), "AGENT_RUN_STARTED", "Agent run started: " + run.runId());
        pollAgentRun(run.runId(), job.getId());

        Files.isDirectory(outputPath);
        addEvent(job.getId(), "AGENT_COMPLETE", "Agent execution completed");
    }

    void pollAgentRun(String runId, UUID jobId) {
        long deadline = System.currentTimeMillis() + (long) properties.getAgentTimeoutSeconds() * 1000;
        while (System.currentTimeMillis() < deadline) {
            AgentRunDto run = agentClient.getLatestRun(runId).orElse(null);
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