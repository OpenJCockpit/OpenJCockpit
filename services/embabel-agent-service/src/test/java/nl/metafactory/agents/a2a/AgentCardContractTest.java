package nl.metafactory.agents.a2a;

import com.embabel.agent.api.tool.Tool;
import com.embabel.common.ai.model.ModelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.spec.AgentCard;
import nl.metafactory.agents.approval.ApprovalDecisionAuditRepository;
import nl.metafactory.agents.approval.ApprovalDecisionService;
import nl.metafactory.agents.approval.ApprovalGateContextAssembler;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.definition.tool.RegisteredAgentTool;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

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
        "openjcockpit.agent-definitions.a2a.public-base-url=http://embabel-agent-service:8081/",
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
    @MockitoBean AgentOrchestrator orchestrator;
    @MockitoBean ApprovalGateRegistry approvalGateRegistry;
    @MockitoBean ApprovalGateContextAssembler approvalGateContextAssembler;
    @MockitoBean ApprovalDecisionService approvalDecisionService;
    @MockitoBean ApprovalDecisionAuditRepository approvalDecisionAuditRepository;
    @MockitoBean nl.metafactory.agents.workflowtrigger.WorkflowTriggerCoordinator workflowTriggerCoordinator;

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void seed() {
        jdbc.sql("DELETE FROM agent_tenant_bindings").update();
        jdbc.sql("""
                INSERT INTO agent_tenant_bindings (tenant_id, agent_id, enabled, model_alias, pinned_version,
                  fallback_allowed, fallback_on_not_found, updated_at)
                VALUES ('tenant-a', 'customer-support', TRUE, NULL, NULL, FALSE, FALSE, ?)""")
                .params(OffsetDateTime.now()).update();
        jdbc.sql("INSERT INTO agent_tenant_binding_tools VALUES ('tenant-a','customer-support','customer.read')").update();
    }

    @Test
    void cardIsAValidA2ACardForTheRealEndpoint() throws Exception {
        String body = mockMvc.perform(get("/a2a/customer-support/.well-known/agent-card.json")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "tenant-a"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        AgentCard card = new ObjectMapper().readValue(body, AgentCard.class);
        assertThat(card.protocolVersion()).isEqualTo("0.3.0");
        assertThat(card.url()).isEqualTo("http://embabel-agent-service:8081/a2a/customer-support");
        assertThat(card.preferredTransport()).isEqualTo("JSONRPC");
        assertThat(card.version()).isEqualTo("1.0.0");
        assertThat(card.description()).isEqualTo("Ondersteunt klantvragen");
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
