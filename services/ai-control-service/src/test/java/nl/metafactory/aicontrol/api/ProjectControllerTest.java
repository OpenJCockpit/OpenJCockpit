package nl.metafactory.aicontrol.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.model.GitStatus;
import nl.metafactory.aicontrol.model.NewProjectFlagRequest;
import nl.metafactory.aicontrol.model.ProjectDto;
import nl.metafactory.aicontrol.model.ProjectRequest;
import nl.metafactory.aicontrol.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ProjectController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private ProjectService service;

    @Test
    void getActiveProjectsReturnsOnlyActiveProjects() throws Exception {
        var id = UUID.randomUUID();
        when(service.listActive()).thenReturn(List.of(dto(id, "Alpha", (short) 1)));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alpha"))
                .andExpect(jsonPath("$[0].active").value(1));
    }

    @Test
    void getAllProjectsReturnsAllIncludingInactive() throws Exception {
        var id1 = UUID.randomUUID();
        var id2 = UUID.randomUUID();
        when(service.listAll()).thenReturn(List.of(dto(id1, "Alpha", (short) 1), dto(id2, "Beta", (short) 0)));

        mockMvc.perform(get("/api/projects/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void createProjectReturns201() throws Exception {
        var id = UUID.randomUUID();
        var req = new ProjectRequest("New Project", null, null, null, "main", null, null, (short) 0);
        when(service.create(any())).thenReturn(dto(id, "New Project", (short) 1));

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Project"));
    }

    @Test
    void createProjectReturns400WhenNameIsBlank() throws Exception {
        var req = new ProjectRequest("", null, null, null, null, null, null, (short) 0);

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProjectReturns200() throws Exception {
        var id = UUID.randomUUID();
        var req = new ProjectRequest("Updated", null, null, null, null, null, null, (short) 0);
        when(service.update(eq(id), any())).thenReturn(dto(id, "Updated", (short) 1));

        mockMvc.perform(put("/api/projects/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    void updateProjectReturns404WhenNotFound() throws Exception {
        var id = UUID.randomUUID();
        var req = new ProjectRequest("Updated", null, null, null, null, null, null, (short) 0);
        when(service.update(eq(id), any())).thenThrow(new ResponseStatusException(NOT_FOUND));

        mockMvc.perform(put("/api/projects/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void activateProjectReturns204() throws Exception {
        var id = UUID.randomUUID();
        doNothing().when(service).setActive(id, (short) 1);

        mockMvc.perform(patch("/api/projects/{id}/activate", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void deactivateProjectReturns204() throws Exception {
        var id = UUID.randomUUID();
        doNothing().when(service).setActive(id, (short) 0);

        mockMvc.perform(patch("/api/projects/{id}/deactivate", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void activateProjectReturns404WhenNotFound() throws Exception {
        var id = UUID.randomUUID();
        doThrow(new ResponseStatusException(NOT_FOUND)).when(service).setActive(id, (short) 1);

        mockMvc.perform(patch("/api/projects/{id}/activate", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivateProjectReturns404WhenNotFound() throws Exception {
        var id = UUID.randomUUID();
        doThrow(new ResponseStatusException(NOT_FOUND)).when(service).setActive(id, (short) 0);

        mockMvc.perform(patch("/api/projects/{id}/deactivate", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void setNewProjectReturns204() throws Exception {
        var id = UUID.randomUUID();
        doNothing().when(service).setNewProject(id, (short) 1);

        var req = new NewProjectFlagRequest((short) 1);
        mockMvc.perform(patch("/api/projects/{id}/new-project", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    @Test
    void setNewProjectReturns404WhenNotFound() throws Exception {
        var id = UUID.randomUUID();
        doThrow(new ResponseStatusException(NOT_FOUND)).when(service).setNewProject(id, (short) 0);

        var req = new NewProjectFlagRequest((short) 0);
        mockMvc.perform(patch("/api/projects/{id}/new-project", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    private ProjectDto dto(UUID id, String name, short active) {
        return new ProjectDto(id, name, null, null, null, "main", null, null,
                active, (short) 0, GitStatus.UNKNOWN, null, null, false,
                Instant.now(), Instant.now());
    }
}
