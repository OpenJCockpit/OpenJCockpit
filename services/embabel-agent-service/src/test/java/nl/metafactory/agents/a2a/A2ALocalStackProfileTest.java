package nl.metafactory.agents.a2a;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.common.ai.model.ModelProvider;
import nl.metafactory.agents.approval.ApprovalDecisionAuditRepository;
import nl.metafactory.agents.approval.ApprovalDecisionService;
import nl.metafactory.agents.approval.ApprovalGateContextAssembler;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the real Spring profile {@code local} that docker-compose activates: approved V3 migration +
 * local seed, the shipped agent-definitions folder, A2A enabled and the LiteLLM A2A provider client. Calls the seeded
 * agents as the Keycloak service client would (tenant_id=local); only the LLM is mocked.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK, properties = {
        // own in-memory DB: the repeatable seed must not leak into the shared testdb of other contexts
        "spring.datasource.url=jdbc:h2:mem:a2a-local-stack;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DEFAULT_NULL_ORDERING=HIGH",
        // container paths of the local profile, mapped to the repository for this test
        "AGENT_DEFINITIONS_LOCAL_ROOT=agent-definitions",
        "AGENT_CATALOG_LOCAL_PATH=../../agents",
        // main application.yml binds these from LITELLM_BASE_URL / LITELLM_VIRTUAL_KEY (set in compose)
        "openjcockpit.agent-definitions.litellm.model-gateway-url=http://litellm:4000/v1",
        "openjcockpit.agent-definitions.litellm.model-gateway-key=sk-test"
})
@ActiveProfiles("local")
@AutoConfigureMockMvc
class A2ALocalStackProfileTest {

    @MockitoBean ModelProvider modelProvider;
    @MockitoBean AgentOrchestrator orchestrator;
    @MockitoBean ApprovalGateRegistry approvalGateRegistry;
    @MockitoBean ApprovalGateContextAssembler approvalGateContextAssembler;
    @MockitoBean ApprovalDecisionService approvalDecisionService;
    @MockitoBean ApprovalDecisionAuditRepository approvalDecisionAuditRepository;
    @MockitoBean nl.metafactory.agents.workflowtrigger.WorkflowTriggerCoordinator workflowTriggerCoordinator;
    @MockitoBean Ai ai;

    @Autowired MockMvc mockMvc;
    @Autowired ApplicationContext context;

    static final String SEND = """
            {"jsonrpc":"2.0","id":"1","method":"message/send","params":{"message":{"kind":"message","role":"user",
             "messageId":"m-1","parts":[{"kind":"text","text":"Schrijf requirements voor een wachtwoord-reset."}]}}}""";

    @Test
    void profileRegistersGatewayModelsAndOneLiteLlmProviderClient() {
        assertThat(context.containsBean("agentGatewayLlm-agent-chat")).isTrue();
        assertThat(context.containsBean("agentGatewayLlm-customer-chat")).isTrue();
        var providers = context.getBean(nl.metafactory.agents.a2a.client.A2AProviderClients.class).all();
        assertThat(providers).singleElement().satisfies(p -> {
            assertThat(p.id()).isEqualTo("litellm");
            assertThat(p.name()).isEqualTo("LiteLLM");
        });
    }

    @Test
    void everySeededAgentServesItsCardAndAnswersForTenantLocal() throws Exception {
        PromptRunner runner = mock(PromptRunner.class, Answers.RETURNS_SELF);
        when(ai.withLlmByRole("agent-chat")).thenReturn(runner);
        when(runner.generateText(anyString())).thenReturn("REQ-001 ...");
        var serviceAccount = jwt().jwt(j -> j.claim("tenant_id", "local")
                .claim("preferred_username", "service-account-openjcockpit-a2a"));

        for (String agent : new String[]{"business-analyst", "solution-architect", "software-developer", "qa-engineer"}) {
            mockMvc.perform(get("/a2a/" + agent + "/.well-known/agent-card.json").with(serviceAccount))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").value("http://embabel-agent-service:8081/a2a/" + agent))
                    .andExpect(jsonPath("$.protocolVersion").value("0.3.0"));
            mockMvc.perform(post("/a2a/" + agent).contentType(MediaType.APPLICATION_JSON).content(SEND).with(serviceAccount))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.kind").value("message"))
                    .andExpect(jsonPath("$.result.metadata.agentId").value(agent))
                    .andExpect(jsonPath("$.error").doesNotExist());
        }
    }

    @Test
    void otherTenantsCannotCallTheSeededAgents() throws Exception {
        mockMvc.perform(post("/a2a/business-analyst").contentType(MediaType.APPLICATION_JSON).content(SEND)
                        .with(jwt().jwt(j -> j.claim("tenant_id", "someone-else"))))
                .andExpect(jsonPath("$.error.data.reason").value("TENANT_FORBIDDEN"));
    }
}
