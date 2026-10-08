package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.GitStatus;
import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;
import nl.metafactory.aicontrol.model.PreflightError;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import nl.metafactory.aicontrol.model.WorkflowPreflightResult;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class WorkflowPreflightService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowPreflightService.class);

    private final GitWorkspaceJobService jobService;
    private final ProjectGitCredentialRepository credentialRepository;
    private final GitConnectivityService gitConnectivityService;
    private final ContainerRuntime containerRuntime;
    private final AgenticWorkflowProperties properties;

    public WorkflowPreflightService(GitWorkspaceJobService jobService,
                                     ProjectGitCredentialRepository credentialRepository,
                                     GitConnectivityService gitConnectivityService,
                                     ContainerRuntime containerRuntime,
                                     AgenticWorkflowProperties properties) {
        this.jobService = jobService;
        this.credentialRepository = credentialRepository;
        this.gitConnectivityService = gitConnectivityService;
        this.containerRuntime = containerRuntime;
        this.properties = properties;
    }

    public WorkflowPreflightResult validateBeforeWorkflowStart(UUID projectId) {
        List<PreflightError> errors = new ArrayList<>();
        checkDocker(errors);
        checkGit(projectId, errors);
        return errors.isEmpty() ? WorkflowPreflightResult.ok() : WorkflowPreflightResult.failed(errors);
    }

    private void checkDocker(List<PreflightError> errors) {
        if (!properties.getContainer().isEnabled()) {
            return;
        }
        try {
            containerRuntime.checkAvailability(
                    properties.getContainer().getImage(),
                    properties.getPreflight().getDockerTimeoutSeconds());
        } catch (Exception e) {
            log.warn("Docker preflight check failed: {}", e.getMessage());
            errors.add(new PreflightError(GitWorkspaceJobErrorCode.DOCKER_UNAVAILABLE,
                    "Docker is not available. " + e.getMessage()));
        }
    }

    private void checkGit(UUID projectId, List<PreflightError> errors) {
        Project project;
        try {
            project = jobService.validateSelectedProject(projectId);
        } catch (GitWorkspaceException e) {
            errors.add(new PreflightError(e.getErrorCode(), e.getMessage()));
            return;
        }

        ProjectGitCredential credential = credentialRepository
                .findFirstByProjectIdAndActiveOrderByCreatedAtDesc(projectId, (short) 1)
                .orElse(null);

        var result = gitConnectivityService.checkGitAccess(project.getGitUrl(), credential);
        if (result.status() != GitStatus.ACCESSIBLE) {
            errors.add(new PreflightError(mapGitErrorCode(result), result.message()));
        }
    }

    private GitWorkspaceJobErrorCode mapGitErrorCode(GitConnectivityService.CheckResult result) {
        String message = result.message() == null ? "" : result.message().toLowerCase(Locale.ROOT);
        if (result.status() == GitStatus.CHECK_FAILED) {
            return GitWorkspaceJobErrorCode.GIT_REPOSITORY_UNAVAILABLE;
        }
        if (message.contains("authentication")) {
            return GitWorkspaceJobErrorCode.GIT_AUTH_FAILED;
        }
        if (message.contains("not found")) {
            return GitWorkspaceJobErrorCode.GIT_REPOSITORY_NOT_FOUND;
        }
        if (message.contains("timed out")) {
            return GitWorkspaceJobErrorCode.TIMEOUT;
        }
        return GitWorkspaceJobErrorCode.GIT_REPOSITORY_UNAVAILABLE;
    }
}
