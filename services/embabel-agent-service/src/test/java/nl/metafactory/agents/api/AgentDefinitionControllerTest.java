package nl.metafactory.agents.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.agents.model.AgentDefinition;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import nl.metafactory.agents.workflow.AgentSpecRepository;
import nl.metafactory.agents.workflow.PromptToDefinitionService;
import nl.metafactory.agents.workflow.model.AgentSpec;
import nl.metafactory.agents.workflow.model.PromptRequest;
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
        controllers = AgentDefinitionController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class AgentDefinitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private AgentSpecRepository repository;

    @MockitoBean
    private PromptToDefinitionService generationService;

    @MockitoBean
    private AgentOrchestrator orchestrator;

    private AgentSpec agent(String name) {
        return new AgentSpec(name, "desc", "role", "instructions", List.of(), List.of(), List.of(), "wf-1", false);
    }

    private AgentDefinition builtInDefinition(String id) {
        return new AgentDefinition(id, id + " Agent", "does things", "engineering",
                List.of(), List.of("output.md"), 0, "In", "Out");
    }

    @Test
    void listReturnsAllAgentDefinitions() throws Exception {
        when(repository.findAll()).thenReturn(List.of(agent("triage-agent")));

        mockMvc.perform(get("/api/agent-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("triage-agent"));
    }

    @Test
    void getReturnsAgentDefinitionByName() throws Exception {
        when(repository.findByName("triage-agent")).thenReturn(Optional.of(agent("triage-agent")));

        mockMvc.perform(get("/api/agent-definitions/triage-agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("role"));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(repository.findByName("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/agent-definitions/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturns201() throws Exception {
        var spec = agent("triage-agent");
        when(repository.save(any())).thenReturn(spec);

        mockMvc.perform(post("/api/agent-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spec)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("triage-agent"));
    }

    @Test
    void updateReturnsUpdatedDefinition() throws Exception {
        var spec = agent("triage-agent");
        when(repository.findByName("triage-agent")).thenReturn(Optional.of(spec));
        when(repository.save(any())).thenReturn(spec);

        mockMvc.perform(put("/api/agent-definitions/triage-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spec)))
                .andExpect(status().isOk());
    }

    @Test
    void updateReturns404WhenMissing() throws Exception {
        var spec = agent("missing");
        when(repository.findByName("missing")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/agent-definitions/missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spec)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/agent-definitions/triage-agent"))
                .andExpect(status().isNoContent());
    }

    @Test
    void generateFromPromptReturnsDraftSpec() throws Exception {
        var draft = agent("triage-agent");
        when(generationService.generateAgentSpec("Triage incoming issues")).thenReturn(draft);

        mockMvc.perform(post("/api/agent-definitions/generate-from-prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PromptRequest("Triage incoming issues"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("triage-agent"));
    }

    @Test
    void listIncludesBuiltInPipelineAgentsAlongsideCustomOnes() throws Exception {
        when(repository.findAll()).thenReturn(List.of(agent("triage-agent")));
        when(orchestrator.availableAgents()).thenReturn(List.of(builtInDefinition("realisation")));

        mockMvc.perform(get("/api/agent-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("triage-agent"))
                .andExpect(jsonPath("$[0].builtIn").value(false))
                .andExpect(jsonPath("$[1].name").value("realisation"))
                .andExpect(jsonPath("$[1].builtIn").value(true));
    }

    @Test
    void getReturnsBuiltInAgentWhenNotInRepository() throws Exception {
        when(repository.findByName("realisation")).thenReturn(Optional.empty());
        when(orchestrator.availableAgents()).thenReturn(List.of(builtInDefinition("realisation")));

        mockMvc.perform(get("/api/agent-definitions/realisation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.builtIn").value(true));
    }

    @Test
    void updateBuiltInAgentReturns409() throws Exception {
        when(orchestrator.availableAgents()).thenReturn(List.of(builtInDefinition("realisation")));
        var spec = agent("realisation");

        mockMvc.perform(put("/api/agent-definitions/realisation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spec)))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteBuiltInAgentReturns409() throws Exception {
        when(orchestrator.availableAgents()).thenReturn(List.of(builtInDefinition("realisation")));

        mockMvc.perform(delete("/api/agent-definitions/realisation"))
                .andExpect(status().isConflict());
    }
}
