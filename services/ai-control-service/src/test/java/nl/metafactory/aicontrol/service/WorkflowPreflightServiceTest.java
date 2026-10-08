package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.GitStatus;
import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowPreflightServiceTest {

    private GitWorkspaceJobService jobService;
    private ProjectGitCredentialRepository credentialRepository;
    private GitConnectivityService gitConnectivityService;
    private ContainerRuntime containerRuntime;
    private AgenticWorkflowProperties properties;
    private WorkflowPreflightService service;

    private UUID projectId;
    private Project project;

    @BeforeEach
    void setUp() {
        jobService = mock(GitWorkspaceJobService.class);
        credentialRepository = mock(ProjectGitCredentialRepository.class);
        gitConnectivityService = mock(GitConnectivityService.class);
        containerRuntime = mock(ContainerRuntime.class);
        properties = new AgenticWorkflowProperties();

        service = new WorkflowPreflightService(jobService, credentialRepository,
                gitConnectivityService, containerRuntime, properties);

        projectId = UUID.randomUUID();
        project = new Project();
        project.setId(projectId);
        project.setGitUrl("https://github.com/org/repo");
        project.setActive((short) 1);

        when(jobService.validateSelectedProject(projectId)).thenReturn(project);
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(eq(projectId), eq((short) 1)))
                .thenReturn(Optional.empty());
        when(gitConnectivityService.checkGitAccess(any(), any()))
                .thenReturn(new GitConnectivityService.CheckResult(GitStatus.ACCESSIBLE, "Reachable"));
    }

    @Test
    void passesWhenDockerAvailableAndGitAccessible() throws Exception {
        var result = service.validateBeforeWorkflowStart(projectId);

        assertThat(result.passed()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void failsWithDockerUnavailable_whenDockerCheckThrows() throws Exception {
        doThrow(new RuntimeException("daemon unreachable"))
                .when(containerRuntime).checkAvailability(any(), any(int.class));

        var result = service.validateBeforeWorkflowStart(projectId);

        assertThat(result.passed()).isFalse();
        assertThat(result.errors())
                .extracting(e -> e.code())
                .containsExactly(GitWorkspaceJobErrorCode.DOCKER_UNAVAILABLE);
    }

    @Test
    void skipsDockerCheck_whenContainerDisabled() throws Exception {
        properties.getContainer().setEnabled(false);

        var result = service.validateBeforeWorkflowStart(projectId);

        assertThat(result.passed()).isTrue();
        verify(containerRuntime, never()).checkAvailability(any(), any(int.class));
    }

    @Test
    void failsWithProjectCode_whenNoProjectSelected() {
        when(jobService.validateSelectedProject(null))
                .thenThrow(new GitWorkspaceException(GitWorkspaceJobErrorCode.NO_SELECTED_PROJECT, "No project selected"));

        var result = service.validateBeforeWorkflowStart(null);

        assertThat(result.passed()).isFalse();
        assertThat(result.errors())
                .extracting(e -> e.code())
                .containsExactly(GitWorkspaceJobErrorCode.NO_SELECTED_PROJECT);
    }

    @Test
    void failsWithProjectInactiveCode_andSkipsGitCheck() {
        when(jobService.validateSelectedProject(projectId))
                .thenThrow(new GitWorkspaceException(GitWorkspaceJobErrorCode.PROJECT_INACTIVE, "Project is inactive"));

        var result = service.validateBeforeWorkflowStart(projectId);

        assertThat(result.errors())
                .extracting(e -> e.code())
                .containsExactly(GitWorkspaceJobErrorCode.PROJECT_INACTIVE);
        verify(gitConnectivityService, never()).checkGitAccess(any(), any());
    }

    @Test
    void failsWithGitUrlMissingCode() {
        when(jobService.validateSelectedProject(projectId))
                .thenThrow(new GitWorkspaceException(GitWorkspaceJobErrorCode.GIT_URL_MISSING, "No Git URL"));

        var result = service.validateBeforeWorkflowStart(projectId);

        assertThat(result.errors())
                .extracting(e -> e.code())
                .containsExactly(GitWorkspaceJobErrorCode.GIT_URL_MISSING);
    }

    @Test
    void failsWithGitAuthFailed_whenMessageIndicatesAuth() {
        when(gitConnectivityService.checkGitAccess(any(), any()))
                .thenReturn(new GitConnectivityService.CheckResult(GitStatus.NOT_ACCESSIBLE, "Authentication failed"));

        var result = service.validateBeforeWorkflowStart(projectId);

        assertThat(result.errors())
                .extracting(e -> e.code())
                .containsExactly(GitWorkspaceJobErrorCode.GIT_AUTH_FAILED);
    }

    @Test
    void failsWithGitRepositoryNotFound_whenMessageIndicatesNotFound() {
        when(gitConnectivityService.checkGitAccess(any(), any()))
                .thenReturn(new GitConnectivityService.CheckResult(GitStatus.NOT_ACCESSIBLE, "Repository not found"));

        var result = service.validateBeforeWorkflowStart(projectId);

        assertThat(result.errors())
                .extracting(e -> e.code())
                .containsExactly(GitWorkspaceJobErrorCode.GIT_REPOSITORY_NOT_FOUND);
    }

    @Test
    void failsWithTimeout_whenMessageIndicatesTimeout() {
        when(gitConnectivityService.checkGitAccess(any(), any()))
                .thenReturn(new GitConnectivityService.CheckResult(GitStatus.NOT_ACCESSIBLE, "Connection timed out"));

        var result = service.validateBeforeWorkflowStart(projectId);

        assertThat(result.errors())
                .extracting(e -> e.code())
                .containsExactly(GitWorkspaceJobErrorCode.TIMEOUT);
    }

    @Test
    void failsWithGitRepositoryUnavailable_whenNotAccessibleWithUnmatchedMessage() {
        when(gitConnectivityService.checkGitAccess(any(), any()))
                .thenReturn(new GitConnectivityService.CheckResult(GitStatus.NOT_ACCESSIBLE, "Unknown network error"));

        var result = service.validateBeforeWorkflowStart(projectId);

        assertThat(result.errors())
                .extracting(e -> e.code())
                .containsExactly(GitWorkspaceJobErrorCode.GIT_REPOSITORY_UNAVAILABLE);
    }

    @Test
    void failsWithGitRepositoryUnavailable_whenCheckFailed() {
        when(gitConnectivityService.checkGitAccess(any(), any()))
                .thenReturn(new GitConnectivityService.CheckResult(GitStatus.CHECK_FAILED, "Unknown error"));

        var result = service.validateBeforeWorkflowStart(projectId);

        assertThat(result.errors())
                .extracting(e -> e.code())
                .containsExactly(GitWorkspaceJobErrorCode.GIT_REPOSITORY_UNAVAILABLE);
    }

    @Test
    void failsWithBothErrors_whenDockerAndGitBothFail() throws Exception {
        doThrow(new RuntimeException("daemon unreachable"))
                .when(containerRuntime).checkAvailability(any(), any(int.class));
        when(gitConnectivityService.checkGitAccess(any(), any()))
                .thenReturn(new GitConnectivityService.CheckResult(GitStatus.NOT_ACCESSIBLE, "Repository not found"));

        var result = service.validateBeforeWorkflowStart(projectId);

        assertThat(result.passed()).isFalse();
        assertThat(result.errors())
                .extracting(e -> e.code())
                .containsExactlyInAnyOrder(
                        GitWorkspaceJobErrorCode.DOCKER_UNAVAILABLE,
                        GitWorkspaceJobErrorCode.GIT_REPOSITORY_NOT_FOUND);
    }

    @Test
    void looksUpAndPassesActiveCredential_whenPresent() {
        var credential = new ProjectGitCredential();
        credential.setProjectId(projectId);
        credential.setUsername("user");
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(eq(projectId), eq((short) 1)))
                .thenReturn(Optional.of(credential));

        service.validateBeforeWorkflowStart(projectId);

        verify(gitConnectivityService).checkGitAccess(project.getGitUrl(), credential);
    }
}
