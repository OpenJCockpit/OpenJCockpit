package nl.metafactory.agents.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.agents.workflow.PromptToDefinitionService;
import nl.metafactory.agents.workflow.SubagentSpecRepository;
import nl.metafactory.agents.workflow.model.PromptRequest;
import nl.metafactory.agents.workflow.model.SubagentSpec;
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
        controllers = SubagentDefinitionController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class SubagentDefinitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private SubagentSpecRepository repository;

    @MockitoBean
    private PromptToDefinitionService generationService;

    private SubagentSpec subagent(String name) {
        return new SubagentSpec(name, "parent", "desc", "responsibilities", "instructions", List.of(), List.of(), "wf-1");
    }

    @Test
    void listReturnsAllSubagentDefinitions() throws Exception {
        when(repository.findAll()).thenReturn(List.of(subagent("log-collector")));

        mockMvc.perform(get("/api/subagent-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("log-collector"));
    }

    @Test
    void getReturnsSubagentDefinitionByName() throws Exception {
        when(repository.findByName("log-collector")).thenReturn(Optional.of(subagent("log-collector")));

        mockMvc.perform(get("/api/subagent-definitions/log-collector"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentAgent").value("parent"));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(repository.findByName("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/subagent-definitions/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturns201() throws Exception {
        var spec = subagent("log-collector");
        when(repository.save(any())).thenReturn(spec);

        mockMvc.perform(post("/api/subagent-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spec)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("log-collector"));
    }

    @Test
    void updateReturnsUpdatedDefinition() throws Exception {
        var spec = subagent("log-collector");
        when(repository.findByName("log-collector")).thenReturn(Optional.of(spec));
        when(repository.save(any())).thenReturn(spec);

        mockMvc.perform(put("/api/subagent-definitions/log-collector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spec)))
                .andExpect(status().isOk());
    }

    @Test
    void updateReturns404WhenMissing() throws Exception {
        var spec = subagent("missing");
        when(repository.findByName("missing")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/subagent-definitions/missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spec)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/subagent-definitions/log-collector"))
                .andExpect(status().isNoContent());
    }

    @Test
    void generateFromPromptReturnsDraftSpec() throws Exception {
        var draft = subagent("log-collector");
        when(generationService.generateSubagentSpec("Collect logs")).thenReturn(draft);

        mockMvc.perform(post("/api/subagent-definitions/generate-from-prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PromptRequest("Collect logs"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("log-collector"));
    }
}
