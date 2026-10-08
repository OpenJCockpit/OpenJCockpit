package nl.metafactory.aicontrol.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.client.AgentSpecDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.PromptRequestDto;
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
        controllers = AgentDefinitionController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class AgentDefinitionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper objectMapper;
    @MockitoBean EmbabelAgentClient client;

    private AgentSpecDto agent(String name) {
        return new AgentSpecDto(name, "desc", "role", "instructions", List.of(), List.of(), List.of(), "wf-1", false);
    }

    @Test
    void listReturnsAgentDefinitions() throws Exception {
        when(client.listAgentSpecs()).thenReturn(List.of(agent("triage-agent")));

        mockMvc.perform(get("/api/agent-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("triage-agent"));
    }

    @Test
    void getReturnsAgentDefinition() throws Exception {
        when(client.getAgentSpec("triage-agent")).thenReturn(Optional.of(agent("triage-agent")));

        mockMvc.perform(get("/api/agent-definitions/triage-agent"))
                .andExpect(status().isOk());
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(client.getAgentSpec("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/agent-definitions/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturns201() throws Exception {
        when(client.createAgentSpec(any())).thenReturn(agent("triage-agent"));

        mockMvc.perform(post("/api/agent-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(agent("triage-agent"))))
                .andExpect(status().isCreated());
    }

    @Test
    void updateReturnsUpdatedDefinition() throws Exception {
        when(client.updateAgentSpec(any(), any())).thenReturn(agent("triage-agent"));

        mockMvc.perform(put("/api/agent-definitions/triage-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(agent("triage-agent"))))
                .andExpect(status().isOk());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/agent-definitions/triage-agent"))
                .andExpect(status().isNoContent());

        verify(client).deleteAgentSpec("triage-agent");
    }

    @Test
    void generateFromPromptReturnsDraft() throws Exception {
        when(client.generateAgentSpec("Triage incoming issues")).thenReturn(agent("triage-agent"));

        mockMvc.perform(post("/api/agent-definitions/generate-from-prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PromptRequestDto("Triage incoming issues"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("triage-agent"));
    }
}
