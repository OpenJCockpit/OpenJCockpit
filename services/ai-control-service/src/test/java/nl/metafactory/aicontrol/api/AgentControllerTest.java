package nl.metafactory.aicontrol.api;

import nl.metafactory.common.test.UnsecuredWebMvcTest;
import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.client.AgentRunDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

@UnsecuredWebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper objectMapper;
    @MockitoBean EmbabelAgentClient embabelAgentClient;

    // Runs start only through a workflow; the direct agent-run start and the built-in agent
    // listing were removed with the built-in pipeline.
    @Test
    void directAgentRunStartAndBuiltInAgentListingNoLongerExist() throws Exception {
        mockMvc.perform(post("/api/agent-runs").contentType(MediaType.APPLICATION_JSON).content("{}"))
               .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/agents"))
               .andExpect(status().is4xxClientError());
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
