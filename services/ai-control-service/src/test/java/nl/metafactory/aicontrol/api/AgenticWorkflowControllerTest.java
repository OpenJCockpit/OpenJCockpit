package nl.metafactory.aicontrol.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.model.GitWorkspaceJobDto;
import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;
import nl.metafactory.aicontrol.model.GitWorkspaceJobEventDto;
import nl.metafactory.aicontrol.model.GitWorkspaceJobStatus;
import nl.metafactory.aicontrol.model.PreflightError;
import nl.metafactory.aicontrol.model.StartWorkflowRequest;
import nl.metafactory.aicontrol.model.WorkflowPreflightResult;
import nl.metafactory.aicontrol.service.GitWorkspaceJobService;
import nl.metafactory.aicontrol.service.WorkflowPreflightService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AgenticWorkflowController.class)
class AgenticWorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private GitWorkspaceJobService jobService;

    @MockitoBean
    private WorkflowPreflightService preflightService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void startWorkflowReturns202AndJobDto() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        var dto = jobDto(jobId, projectId);
        when(preflightService.validateBeforeWorkflowStart(projectId)).thenReturn(WorkflowPreflightResult.ok());
        when(jobService.createJob(eq(projectId), eq("spec-001"), eq("alice"))).thenReturn(dto);
        doNothing().when(jobService).runWorkflowAsync(jobId);

        var req = new StartWorkflowRequest("spec-001", projectId, null);
        mockMvc.perform(post("/api/agentic-workflows/start")
                        .with(jwt().jwt(b -> b.claim("preferred_username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.preflightPassed").value(true))
                .andExpect(jsonPath("$.jobId").value(jobId.toString()));

        verify(jobService).createJob(projectId, "spec-001", "alice");
    }

    @Test
    void startWorkflowUsesUnknownUsernameWithoutAuthentication() {
        SecurityContextHolder.clearContext();
        GitWorkspaceJobService directJobService = org.mockito.Mockito.mock(GitWorkspaceJobService.class);
        WorkflowPreflightService directPreflightService = org.mockito.Mockito.mock(WorkflowPreflightService.class);
        UUID projectId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(directPreflightService.validateBeforeWorkflowStart(projectId)).thenReturn(WorkflowPreflightResult.ok());
        when(directJobService.createJob(eq(projectId), eq("spec-001"), eq("unknown")))
                .thenReturn(jobDto(jobId, projectId));

        var req = new StartWorkflowRequest("spec-001", projectId, null);
        new AgenticWorkflowController(directJobService, directPreflightService).startWorkflow(req);

        verify(directJobService).createJob(projectId, "spec-001", "unknown");
    }

    @Test
    void startWorkflowReturns400WhenSpecFileRefBlank() throws Exception {
        var req = new StartWorkflowRequest("", UUID.randomUUID(), null);
        mockMvc.perform(post("/api/agentic-workflows/start")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void startWorkflowRequiresAuthentication() throws Exception {
        var req = new StartWorkflowRequest("spec-001", UUID.randomUUID(), null);
        mockMvc.perform(post("/api/agentic-workflows/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void startWorkflowReturns409AndDoesNotCreateJob_whenDockerUnavailable() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(preflightService.validateBeforeWorkflowStart(projectId)).thenReturn(
                WorkflowPreflightResult.failed(List.of(new PreflightError(
                        GitWorkspaceJobErrorCode.DOCKER_UNAVAILABLE, "Docker is not available."))));

        var req = new StartWorkflowRequest("spec-001", projectId, null);
        mockMvc.perform(post("/api/agentic-workflows/start")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.preflightPassed").value(false))
                .andExpect(jsonPath("$.errors[0].code").value("DOCKER_UNAVAILABLE"));

        verify(jobService, never()).createJob(any(), any(), any());
        verify(jobService, never()).runWorkflowAsync(any());
    }

    @Test
    void startWorkflowReturns409AndDoesNotCreateJob_whenGitRepositoryUnavailable() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(preflightService.validateBeforeWorkflowStart(projectId)).thenReturn(
                WorkflowPreflightResult.failed(List.of(new PreflightError(
                        GitWorkspaceJobErrorCode.GIT_REPOSITORY_UNAVAILABLE, "Repository not reachable."))));

        var req = new StartWorkflowRequest("spec-001", projectId, null);
        mockMvc.perform(post("/api/agentic-workflows/start")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("GIT_REPOSITORY_UNAVAILABLE"));

        verify(jobService, never()).createJob(any(), any(), any());
        verify(jobService, never()).runWorkflowAsync(any());
    }

    @Test
    void startWorkflowReturns409WithBothErrors_whenDockerAndGitBothFail() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(preflightService.validateBeforeWorkflowStart(projectId)).thenReturn(
                WorkflowPreflightResult.failed(List.of(
                        new PreflightError(GitWorkspaceJobErrorCode.DOCKER_UNAVAILABLE, "Docker is not available."),
                        new PreflightError(GitWorkspaceJobErrorCode.GIT_REPOSITORY_UNAVAILABLE, "Repository not reachable."))));

        var req = new StartWorkflowRequest("spec-001", projectId, null);
        mockMvc.perform(post("/api/agentic-workflows/start")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.length()").value(2));

        verify(jobService, never()).createJob(any(), any(), any());
        verify(jobService, never()).runWorkflowAsync(any());
    }

    @Test
    void startWorkflowReturns409_whenNoProjectSelected() throws Exception {
        when(preflightService.validateBeforeWorkflowStart(null)).thenReturn(
                WorkflowPreflightResult.failed(List.of(new PreflightError(
                        GitWorkspaceJobErrorCode.NO_SELECTED_PROJECT, "No project selected"))));

        var req = new StartWorkflowRequest("spec-001", null, null);
        mockMvc.perform(post("/api/agentic-workflows/start")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("NO_SELECTED_PROJECT"));

        verify(jobService, never()).createJob(any(), any(), any());
        verify(jobService, never()).runWorkflowAsync(any());
    }

    @Test
    void preflightEndpointReturnsResult_andNeverTouchesJobService() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(preflightService.validateBeforeWorkflowStart(projectId)).thenReturn(WorkflowPreflightResult.ok());

        mockMvc.perform(post("/api/agentic-workflows/preflight")
                        .param("projectId", projectId.toString())
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));

        verify(jobService, never()).createJob(any(), any(), any());
        verify(jobService, never()).runWorkflowAsync(any());
    }

    @Test
    void preflightEndpointReturnsFailedResult_whenChecksFail() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(preflightService.validateBeforeWorkflowStart(projectId)).thenReturn(
                WorkflowPreflightResult.failed(List.of(new PreflightError(
                        GitWorkspaceJobErrorCode.DOCKER_UNAVAILABLE, "Docker is not available."))));

        mockMvc.perform(post("/api/agentic-workflows/preflight")
                        .param("projectId", projectId.toString())
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.errors[0].code").value("DOCKER_UNAVAILABLE"));
    }

    @Test
    void getJobReturns200WithDto() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(jobService.findById(jobId)).thenReturn(jobDto(jobId, UUID.randomUUID()));

        mockMvc.perform(get("/api/git-workspace-jobs/{id}", jobId)
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId.toString()));
    }

    @Test
    void getJobReturns404WhenNotFound() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(jobService.findById(jobId)).thenThrow(new ResponseStatusException(NOT_FOUND));

        mockMvc.perform(get("/api/git-workspace-jobs/{id}", jobId)
                        .with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getJobEventsReturns200WithList() throws Exception {
        UUID jobId = UUID.randomUUID();
        var event = new GitWorkspaceJobEventDto(UUID.randomUUID(), jobId,
                "JOB_CREATED", "Job created", Instant.now());
        when(jobService.findEvents(jobId)).thenReturn(List.of(event));

        mockMvc.perform(get("/api/git-workspace-jobs/{id}/events", jobId)
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("JOB_CREATED"));
    }

    @Test
    void getJobEventsReturns404WhenJobNotFound() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(jobService.findEvents(jobId)).thenThrow(new ResponseStatusException(NOT_FOUND));

        mockMvc.perform(get("/api/git-workspace-jobs/{id}/events", jobId)
                        .with(jwt()))
                .andExpect(status().isNotFound());
    }

    private GitWorkspaceJobDto jobDto(UUID id, UUID projectId) {
        return new GitWorkspaceJobDto(id, projectId, "spec-001",
                GitWorkspaceJobStatus.CREATED, "main", null, null,
                null, null, "alice", Instant.now(), null,
                Instant.now(), Instant.now());
    }
}
