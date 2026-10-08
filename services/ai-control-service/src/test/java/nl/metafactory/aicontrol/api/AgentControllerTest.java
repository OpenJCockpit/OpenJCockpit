package nl.metafactory.aicontrol.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.client.AgentDefinitionDto;
import nl.metafactory.aicontrol.client.AgentRunDto;
import nl.metafactory.aicontrol.client.AgentRunRequestDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AgentController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class AgentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper objectMapper;
    @MockitoBean EmbabelAgentClient embabelAgentClient;

    @Test
    void getAgentsReturnsDefinitionsInSequenceOrder() throws Exception {
        when(embabelAgentClient.getAgentDefinitions()).thenReturn(List.of(
                new AgentDefinitionDto("requirement", "Requirement Agent", "Retrieve requirements", "specification",
                        0, "SpecContent", "RequirementAnalysis"),
                new AgentDefinitionDto("impact", "Impact Analysis Agent", "Impact analysis", "analysis",
                        1, "RequirementAnalysis", "ImpactReport")
        ));

        mockMvc.perform(get("/api/agents"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].id").value("requirement"))
               .andExpect(jsonPath("$[0].sequenceOrder").value(0))
               .andExpect(jsonPath("$[0].outputType").value("RequirementAnalysis"))
               .andExpect(jsonPath("$[1].id").value("impact"))
               .andExpect(jsonPath("$[1].inputType").value("RequirementAnalysis"));
    }

    @Test
    void getAgentsReturnsEmptyListWhenUnavailable() throws Exception {
        when(embabelAgentClient.getAgentDefinitions()).thenReturn(List.of());

        mockMvc.perform(get("/api/agents"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isArray())
               .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void postAgentRunForwardsToEmbabelAndReturns202() throws Exception {
        var request = new AgentRunRequestDto("cust1", "spec content", List.of("requirement"),
                "user", "https://github.com/org/repo");
        var run = new AgentRunDto("run-1", "cust1", "spec content",
                "https://github.com/org/repo", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null);
        when(embabelAgentClient.startAgentRun(any())).thenReturn(run);

        mockMvc.perform(post("/api/agent-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isAccepted())
               .andExpect(jsonPath("$.runId").value("run-1"))
               .andExpect(jsonPath("$.repositoryUrl").value("https://github.com/org/repo"))
               .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void getAgentRunProxiesToEmbabelAndReturnsRun() throws Exception {
        var run = new AgentRunDto("run-42", "cust2", "spec.md",
                "https://github.com/org/repo", "COMPLETED", Instant.now(), List.of(), List.of(), null, null, null, null);
        when(embabelAgentClient.getLatestRun("run-42")).thenReturn(Optional.of(run));

        mockMvc.perform(get("/api/agent-runs/run-42"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.runId").value("run-42"))
               .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getAgentRunReturns404WhenRunNotFound() throws Exception {
        when(embabelAgentClient.getLatestRun("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/agent-runs/missing"))
               .andExpect(status().isNotFound());
    }

    @Test
    void deleteAgentRunStopsRunAndReturns204() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/agent-runs/run-77"))
               .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(embabelAgentClient).stopAgentRun("run-77");
    }
}
