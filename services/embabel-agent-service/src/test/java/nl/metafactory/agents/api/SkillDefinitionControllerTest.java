package nl.metafactory.agents.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.agents.workflow.PromptToDefinitionService;
import nl.metafactory.agents.workflow.SkillSpecRepository;
import nl.metafactory.agents.workflow.model.PromptRequest;
import nl.metafactory.agents.workflow.model.SkillSpec;
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private SkillSpecRepository repository;

    @MockitoBean
    private PromptToDefinitionService generationService;

    private SkillSpec skill(String name) {
        return new SkillSpec(name, "desc", "in", "out", "instructions", List.of(), null);
    }

    @Test
    void listReturnsAllSkillDefinitions() throws Exception {
        when(repository.findAll()).thenReturn(List.of(skill("summarize")));

        mockMvc.perform(get("/api/skill-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("summarize"));
    }

    @Test
    void getReturnsSkillDefinitionByName() throws Exception {
        when(repository.findByName("summarize")).thenReturn(Optional.of(skill("summarize")));

        mockMvc.perform(get("/api/skill-definitions/summarize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputContract").value("in"));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(repository.findByName("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/skill-definitions/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturns201() throws Exception {
        var spec = skill("summarize");
        when(repository.save(any())).thenReturn(spec);

        mockMvc.perform(post("/api/skill-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spec)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("summarize"));
    }

    @Test
    void updateReturnsUpdatedDefinition() throws Exception {
        var spec = skill("summarize");
        when(repository.findByName("summarize")).thenReturn(Optional.of(spec));
        when(repository.save(any())).thenReturn(spec);

        mockMvc.perform(put("/api/skill-definitions/summarize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spec)))
                .andExpect(status().isOk());
    }

    @Test
    void updateReturns404WhenMissing() throws Exception {
        var spec = skill("missing");
        when(repository.findByName("missing")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/skill-definitions/missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spec)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/skill-definitions/summarize"))
                .andExpect(status().isNoContent());
    }

    @Test
    void generateFromPromptReturnsDraftSpec() throws Exception {
        var draft = skill("summarize");
        when(generationService.generateSkillSpec("Summarize text")).thenReturn(draft);

        mockMvc.perform(post("/api/skill-definitions/generate-from-prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PromptRequest("Summarize text"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("summarize"));
    }
}
