package nl.metafactory.aicontrol.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.PromptRequestDto;
import nl.metafactory.aicontrol.client.SubagentSpecDto;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SubagentDefinitionController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class SubagentDefinitionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper objectMapper;
    @MockitoBean EmbabelAgentClient client;

    private SubagentSpecDto subagent(String name) {
        return new SubagentSpecDto(name, "parent", "desc", "responsibilities", "instructions", List.of(), List.of(), "wf-1");
    }

    @Test
    void listReturnsSubagentDefinitions() throws Exception {
        when(client.listSubagentSpecs()).thenReturn(List.of(subagent("log-collector")));

        mockMvc.perform(get("/api/subagent-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("log-collector"));
    }

    @Test
    void getReturnsSubagentDefinition() throws Exception {
        when(client.getSubagentSpec("log-collector")).thenReturn(Optional.of(subagent("log-collector")));

        mockMvc.perform(get("/api/subagent-definitions/log-collector"))
                .andExpect(status().isOk());
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(client.getSubagentSpec("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/subagent-definitions/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturns201() throws Exception {
        when(client.createSubagentSpec(any())).thenReturn(subagent("log-collector"));

        mockMvc.perform(post("/api/subagent-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subagent("log-collector"))))
                .andExpect(status().isCreated());
    }

    @Test
    void updateReturnsUpdatedDefinition() throws Exception {
        when(client.updateSubagentSpec(any(), any())).thenReturn(subagent("log-collector"));

        mockMvc.perform(put("/api/subagent-definitions/log-collector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subagent("log-collector"))))
                .andExpect(status().isOk());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/subagent-definitions/log-collector"))
                .andExpect(status().isNoContent());

        verify(client).deleteSubagentSpec("log-collector");
    }

    @Test
    void generateFromPromptReturnsDraft() throws Exception {
        when(client.generateSubagentSpec("Collect logs")).thenReturn(subagent("log-collector"));

        mockMvc.perform(post("/api/subagent-definitions/generate-from-prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PromptRequestDto("Collect logs"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("log-collector"));
    }
}
