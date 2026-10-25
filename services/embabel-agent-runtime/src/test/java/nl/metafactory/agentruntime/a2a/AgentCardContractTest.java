package nl.metafactory.agentruntime.a2a;

import com.embabel.agent.api.tool.Tool;
import com.embabel.common.ai.model.ModelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.spec.AgentCard;
import nl.metafactory.agentruntime.definition.tool.RegisteredAgentTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import nl.metafactory.agentruntime.management.AgentServiceClient;
import nl.metafactory.common.agentruntime.AgentTenantBinding;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;


import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The card must deserialize into the A2A SDK's own {@code io.a2a.spec.AgentCard} (0.3.2.Final),
 * point at the real invocation URL, and advertise neither unimplemented capabilities nor internal
 * skill packages.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK, properties = {
        "openjcockpit.agent-definitions.enabled=true",
        "openjcockpit.agent-definitions.local-root=src/test/resources/agent-definitions",
        "openjcockpit.agent-definitions.a2a.enabled=true",
        "openjcockpit.agent-definitions.a2a.public-base-url=http://embabel-agent-runtime:8082/",
        "embabel.models.llms.customer-chat=test-llm"
})
@AutoConfigureMockMvc
class AgentCardContractTest {

    @TestConfiguration
    static class Tools {
        @Bean
        RegisteredAgentTool customerRead() {
            return new RegisteredAgentTool("customer.read", Tool.create("customer_read", "r", in -> Tool.Result.text("A")));
        }
    }

    @MockitoBean ModelProvider modelProvider;
    @MockitoBean AgentServiceClient agentService;

    @Autowired MockMvc mockMvc;

    @BeforeEach
    void seed() {
        // tenant bindings are management data, served by the agent service
        when(agentService.tenantBinding(anyString(), anyString())).thenReturn(java.util.Optional.empty());
        when(agentService.tenantBinding("tenant-a", "customer-support")).thenReturn(java.util.Optional.of(
                new AgentTenantBinding("tenant-a", "customer-support", true, null, null, false, false,
                        java.util.Set.of("customer.read"), java.util.Set.of())));
    }

    @Test
    void cardIsAValidA2ACardForTheRealEndpoint() throws Exception {
        String body = mockMvc.perform(get("/a2a/customer-support/.well-known/agent-card.json")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "tenant-a"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        AgentCard card = new ObjectMapper().readValue(body, AgentCard.class);
        assertThat(card.protocolVersion()).isEqualTo("0.3.0");
        assertThat(card.url()).isEqualTo("http://embabel-agent-runtime:8082/a2a/customer-support");
        assertThat(card.preferredTransport()).isEqualTo("JSONRPC");
        assertThat(card.version()).isEqualTo("1.0.0");
        assertThat(card.description()).isEqualTo("Handles customer questions");
        assertThat(card.capabilities().streaming()).isFalse();
        assertThat(card.capabilities().pushNotifications()).isFalse();
        assertThat(card.skills()).extracting(s -> s.id()).containsExactly("customer-support");
        assertThat(body).doesNotContain("customer-lookup").doesNotContain("customer.read").doesNotContain("pii-protection");
    }

    @Test
    void legacyWellKnownPathServesTheSameCard() throws Exception {
        mockMvc.perform(get("/a2a/customer-support/.well-known/agent.json")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "tenant-a"))))
                .andExpect(status().isOk());
    }

    @Test
    void unauthorisedTenantsAndUnknownAgentsGetNotFound() throws Exception {
        mockMvc.perform(get("/a2a/customer-support/.well-known/agent-card.json")
                .with(jwt().jwt(j -> j.claim("tenant_id", "tenant-b")))).andExpect(status().isNotFound());
        mockMvc.perform(get("/a2a/customer-support/.well-known/agent-card.json").with(jwt()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/a2a/billing/.well-known/agent-card.json")
                .with(jwt().jwt(j -> j.claim("tenant_id", "tenant-a")))).andExpect(status().isNotFound());
        mockMvc.perform(get("/a2a/customer-support/.well-known/agent-card.json")).andExpect(status().isUnauthorized());
    }
}
