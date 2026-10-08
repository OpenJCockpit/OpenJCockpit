package nl.metafactory.aicontrol.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.PromptRequestDto;
import nl.metafactory.aicontrol.client.SkillSpecDto;
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
        controllers = SkillDefinitionController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class SkillDefinitionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper objectMapper;
    @MockitoBean EmbabelAgentClient client;

    private SkillSpecDto skill(String name) {
        return new SkillSpecDto(name, "desc", "in", "out", "instructions", List.of(), null);
    }

    @Test
    void listReturnsSkillDefinitions() throws Exception {
        when(client.listSkillSpecs()).thenReturn(List.of(skill("summarize")));

        mockMvc.perform(get("/api/skill-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("summarize"));
    }

    @Test
    void getReturnsSkillDefinition() throws Exception {
        when(client.getSkillSpec("summarize")).thenReturn(Optional.of(skill("summarize")));

        mockMvc.perform(get("/api/skill-definitions/summarize"))
                .andExpect(status().isOk());
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(client.getSkillSpec("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/skill-definitions/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturns201() throws Exception {
        when(client.createSkillSpec(any())).thenReturn(skill("summarize"));

        mockMvc.perform(post("/api/skill-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(skill("summarize"))))
                .andExpect(status().isCreated());
    }

    @Test
    void updateReturnsUpdatedDefinition() throws Exception {
        when(client.updateSkillSpec(any(), any())).thenReturn(skill("summarize"));

        mockMvc.perform(put("/api/skill-definitions/summarize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(skill("summarize"))))
                .andExpect(status().isOk());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/skill-definitions/summarize"))
                .andExpect(status().isNoContent());

        verify(client).deleteSkillSpec("summarize");
    }

    @Test
    void generateFromPromptReturnsDraft() throws Exception {
        when(client.generateSkillSpec("Summarize text")).thenReturn(skill("summarize"));

        mockMvc.perform(post("/api/skill-definitions/generate-from-prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PromptRequestDto("Summarize text"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("summarize"));
    }
}
