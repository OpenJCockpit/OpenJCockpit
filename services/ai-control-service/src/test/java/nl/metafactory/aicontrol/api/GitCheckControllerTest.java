package nl.metafactory.aicontrol.api;

import nl.metafactory.common.test.UnsecuredWebMvcTest;
import nl.metafactory.aicontrol.model.GitStatus;
import nl.metafactory.aicontrol.model.ProjectDto;
import nl.metafactory.aicontrol.service.GitCheckRateLimiter;
import nl.metafactory.aicontrol.service.GitConnectivityService;
import nl.metafactory.aicontrol.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@UnsecuredWebMvcTest(GitCheckController.class)
class GitCheckControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitConnectivityService connectivityService;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private GitCheckRateLimiter rateLimiter;

    private static final String STATELESS_BODY = """
            {"gitUrl":"https://git.example.com/org/repo","credentialType":"HTTPS_TOKEN","username":"bob","secret":"s3cret"}
            """;

    @Test
    void checkGitConnectionReturnsStatusAndWarnings_withoutEchoingTheSecret() throws Exception {
        when(connectivityService.checkGitConnection("https://git.example.com/org/repo", "bob", "s3cret", null))
                .thenReturn(new GitConnectivityService.ConnectionCheck(
                        new GitConnectivityService.CheckResult(GitStatus.ACCESSIBLE, "Reachable with credentials"),
                        Set.of("host_private")));

        mockMvc.perform(post("/api/projects/git-check").contentType(APPLICATION_JSON).content(STATELESS_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gitStatus").value("ACCESSIBLE"))
                .andExpect(jsonPath("$.message").value("Reachable with credentials"))
                .andExpect(jsonPath("$.warnings[0]").value("host_private"))
                .andExpect(content().string(not(containsString("s3cret"))));
        verify(rateLimiter).acquire(any());
    }

    @Test
    void checkGitConnectionReturnsNotAccessibleAsOk() throws Exception {
        when(connectivityService.checkGitConnection(any(), any(), any(), any()))
                .thenReturn(new GitConnectivityService.ConnectionCheck(
                        new GitConnectivityService.CheckResult(GitStatus.NOT_ACCESSIBLE, "Authentication failed"),
                        Set.of()));

        mockMvc.perform(post("/api/projects/git-check").contentType(APPLICATION_JSON).content(STATELESS_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gitStatus").value("NOT_ACCESSIBLE"))
                .andExpect(jsonPath("$.warnings").isEmpty());
    }

    @Test
    void checkGitConnectionReturns400ForUnsupportedScheme() throws Exception {
        when(connectivityService.checkGitConnection(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported URL: use https, ssh or git"));

        mockMvc.perform(post("/api/projects/git-check").contentType(APPLICATION_JSON)
                        .content("{\"gitUrl\":\"file:///etc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkGitConnectionReturns400WhenSecretMissingForCredentialType() throws Exception {
        mockMvc.perform(post("/api/projects/git-check").contentType(APPLICATION_JSON)
                        .content("{\"gitUrl\":\"https://git.example.com/r\",\"credentialType\":\"GITHUB_PAT\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(connectivityService);
    }

    @Test
    void checkGitConnectionReturns429WhenRateLimited() throws Exception {
        doThrow(new ResponseStatusException(TOO_MANY_REQUESTS)).when(rateLimiter).acquire(any());

        mockMvc.perform(post("/api/projects/git-check").contentType(APPLICATION_JSON).content(STATELESS_BODY))
                .andExpect(status().isTooManyRequests());
        verifyNoInteractions(connectivityService);
    }

    @Test
    void triggerGitCheckReturns200WithAccessibleStatus() throws Exception {
        var id = UUID.randomUUID();
        when(connectivityService.checkProjectGitAccess(id)).thenReturn(GitStatus.ACCESSIBLE);
        when(projectService.findById(id)).thenReturn(dto(id, GitStatus.ACCESSIBLE));

        mockMvc.perform(post("/api/projects/{id}/git-check", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gitStatus").value("ACCESSIBLE"))
                .andExpect(jsonPath("$.projectId").value(id.toString()));
    }

    @Test
    void triggerGitCheckReturns404WhenProjectNotFound() throws Exception {
        var id = UUID.randomUUID();
        when(connectivityService.checkProjectGitAccess(id))
                .thenThrow(new ResponseStatusException(NOT_FOUND));

        mockMvc.perform(post("/api/projects/{id}/git-check", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void getGitStatusReturnsStoredStatus() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenReturn(dto(id, GitStatus.NOT_ACCESSIBLE));

        mockMvc.perform(get("/api/projects/{id}/git-status", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gitStatus").value("NOT_ACCESSIBLE"));
    }

    @Test
    void getGitStatusReturns404WhenProjectNotFound() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenThrow(new ResponseStatusException(NOT_FOUND));

        mockMvc.perform(get("/api/projects/{id}/git-status", id))
                .andExpect(status().isNotFound());
    }

    private ProjectDto dto(UUID id, GitStatus status) {
        return new ProjectDto(id, "Test", null, null, null, "main", null, null,
                (short) 1, (short) 0, status, Instant.now(), "test message", false,
                Instant.now(), Instant.now());
    }
}
