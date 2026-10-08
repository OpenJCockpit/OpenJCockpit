package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.model.GitStatus;
import nl.metafactory.aicontrol.model.ProjectDto;
import nl.metafactory.aicontrol.service.GitConnectivityService;
import nl.metafactory.aicontrol.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = GitCheckController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class GitCheckControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitConnectivityService connectivityService;

    @MockitoBean
    private ProjectService projectService;

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
