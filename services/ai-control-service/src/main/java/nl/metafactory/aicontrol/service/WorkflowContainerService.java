package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.ExecResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkflowContainerService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowContainerService.class);
    private static final String LABEL_APP = "app";
    private static final String LABEL_APP_VALUE = "agentic-workflow";
    private static final String LABEL_JOB = "jobId";
    private static final String LABEL_PROJECT = "projectId";

    private final ContainerRuntime runtime;
    private final AgenticWorkflowProperties properties;

    public WorkflowContainerService(ContainerRuntime runtime, AgenticWorkflowProperties properties) {
        this.runtime = runtime;
        this.properties = properties;
    }

    public String createWorkflowContainer(UUID jobId, UUID projectId) {
        if (!properties.getContainer().isEnabled()) {
            log.info("Container is disabled, no container created for job {}", jobId);
            return null;
        }
        Map<String, String> labels = Map.of(
                LABEL_APP, LABEL_APP_VALUE,
                LABEL_JOB, jobId.toString(),
                LABEL_PROJECT, projectId.toString()
        );
        String id = runtime.createContainer(
                properties.getContainer().getImage(),
                jobId, projectId,
                properties.getTmpfs().getSizeMb(),
                properties.getContainer().getMemoryMb() * 1024 * 1024,
                labels);
        log.info("Workflow container created: {} for job {}", id, jobId);
        return id;
    }

    public void startContainer(String containerId) {
        if (containerId == null) return;
        runtime.startContainer(containerId);
        log.info("Container started: {}", containerId);
    }

    public ExecResult executeCommandInContainer(String containerId, List<String> command, Duration timeout) {
        if (containerId == null) return new ExecResult(0, "", "");
        var result = runtime.executeInContainer(containerId, command, timeout);
        if (!result.success()) {
            log.warn("Command failed in container {} (exit {}): {}", containerId, result.exitCode(), result.stderr());
        }
        return result;
    }

    public void cleanupContainer(String containerId) {
        if (containerId == null || !properties.isCleanupEnabled()) return;
        try { runtime.stopContainer(containerId); } catch (Exception e) {
            log.warn("Stop container {} failed: {}", containerId, e.getMessage());
        }
        try { runtime.removeContainer(containerId); } catch (Exception e) {
            log.warn("Remove container {} failed: {}", containerId, e.getMessage());
        }
        log.info("Container cleaned up: {}", containerId);
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void cleanupOrphanedContainers() {
        if (!properties.isCleanupEnabled()) return;
        try {
            List<String> ids = runtime.findContainersByLabel(LABEL_APP, LABEL_APP_VALUE);
            log.debug("Orphan cleanup: {} containers found with label {}", ids.size(), LABEL_APP_VALUE);
            for (String id : ids) {
                cleanupContainer(id);
            }
        } catch (Exception e) {
            log.warn("Orphaned container cleanup failed: {}", e.getMessage());
        }
    }
}
