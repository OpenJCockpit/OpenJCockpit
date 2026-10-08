package nl.metafactory.aicontrol.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.WorkflowGroupDto;
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

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = WorkflowGroupController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class WorkflowGroupControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper objectMapper;
    @MockitoBean EmbabelAgentClient client;

    private WorkflowGroupDto group(String id) {
        return new WorkflowGroupDto(id, "Onboarding flows", "Workflows around customer onboarding", "Noordzee Logistics");
    }

    @Test
    void listReturnsGroups() throws Exception {
        when(client.listWorkflowGroups()).thenReturn(List.of(group("wg-1")));

        mockMvc.perform(get("/api/workflow-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("wg-1"));
    }

    @Test
    void getReturnsGroup() throws Exception {
        when(client.getWorkflowGroup("wg-1")).thenReturn(Optional.of(group("wg-1")));

        mockMvc.perform(get("/api/workflow-groups/wg-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Onboarding flows"));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(client.getWorkflowGroup("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/workflow-groups/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturns201() throws Exception {
        when(client.createWorkflowGroup(any())).thenReturn(group("wg-1"));

        mockMvc.perform(post("/api/workflow-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(group("wg-1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("wg-1"));
    }

    @Test
    void updateReturnsUpdatedGroup() throws Exception {
        when(client.updateWorkflowGroup(eq("wg-1"), any())).thenReturn(group("wg-1"));

        mockMvc.perform(put("/api/workflow-groups/wg-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(group("wg-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("wg-1"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/workflow-groups/wg-1"))
                .andExpect(status().isNoContent());

        verify(client).deleteWorkflowGroup("wg-1");
    }
}
