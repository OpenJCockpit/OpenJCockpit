package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.client.AgentCatalogDto;
import nl.metafactory.aicontrol.client.AgentCatalogLocalAgentDto;
import nl.metafactory.aicontrol.client.AgentCatalogLocalSectionDto;
import nl.metafactory.aicontrol.client.AgentCatalogProviderAgentDto;
import nl.metafactory.aicontrol.client.AgentCatalogProviderSectionDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.common.test.UnsecuredWebMvcTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@UnsecuredWebMvcTest(AgentCatalogController.class)
class AgentCatalogControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean EmbabelAgentClient client;

    @Test
    void relaysLocalAndProviderSections() throws Exception {
        var local = new AgentCatalogLocalSectionDto("Local", "OK", null, List.of(new AgentCatalogLocalAgentDto(
                "triage", "Triage", "d", "1.0.0", "agent-chat", "support/triage.md", List.of("support"),
                List.of(), List.of(), true, null)));
        var litellm = new AgentCatalogProviderSectionDto("LiteLLM", "litellm", "OK", null, List.of(
                new AgentCatalogProviderAgentDto("a1", "business-analyst", "Requirements", "http://localhost:4100/a2a/business-analyst",
                        "http://localhost:4100/a2a/business-analyst/.well-known/agent-card.json",
                        "http://embabel-agent-runtime:8082/a2a/business-analyst",
                        "0.3.0", "1.0.0", false, List.of("Business Analyst"))));
        when(client.getAgentCatalog()).thenReturn(Optional.of(new AgentCatalogDto(local, List.of(litellm))));

        mockMvc.perform(get("/api/agent-catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.local.name").value("Local"))
                .andExpect(jsonPath("$.local.agents[0].folders[0]").value("support"))
                .andExpect(jsonPath("$.providers[0].name").value("LiteLLM"))
                .andExpect(jsonPath("$.providers[0].agents[0].name").value("business-analyst"))
                .andExpect(jsonPath("$.providers[0].agents[0].protocolVersion").value("0.3.0"));
    }

    @Test
    void relaysTheHostedAgentCard() throws Exception {
        when(client.getProviderAgentCard("litellm", "qa-engineer")).thenReturn(new EmbabelAgentClient.AgentCardResult(200,
                java.util.Map.of("name", "qa-engineer", "url", "http://localhost:4100/a2a/qa-engineer")));
        mockMvc.perform(get("/api/agent-catalog/providers/litellm/agents/qa-engineer/card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("http://localhost:4100/a2a/qa-engineer"));
    }

    @Test
    void cardFailuresAreRelayed() throws Exception {
        when(client.getProviderAgentCard("litellm", "nope")).thenReturn(new EmbabelAgentClient.AgentCardResult(404, null));
        when(client.getProviderAgentCard("litellm", "down")).thenReturn(new EmbabelAgentClient.AgentCardResult(502, null));
        mockMvc.perform(get("/api/agent-catalog/providers/litellm/agents/nope/card")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/agent-catalog/providers/litellm/agents/down/card")).andExpect(status().isBadGateway());
    }

    @Test
    void unreachableAgentServiceIsBadGateway() throws Exception {
        when(client.getAgentCatalog()).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/agent-catalog")).andExpect(status().isBadGateway());
    }
}
