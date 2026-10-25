package nl.metafactory.agentruntime.a2a;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.common.ai.model.ModelProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import nl.metafactory.agentruntime.management.AgentServiceClient;
import nl.metafactory.common.agentruntime.AgentTenantBinding;
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
 * Runs the real Spring profile {@code local} that docker-compose activates for the runtime: the shipped
 * agent-definitions folder, A2A enabled, the gateway model aliases and the agent service as source of the
 * tenant bindings (mocked here with the rows of its local seed). Calls the seeded
 * agents as the Keycloak service client would (tenant_id=local); only the LLM is mocked.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK, properties = {
        // container path of the local profile, mapped to the repository for this test
        "AGENT_DEFINITIONS_LOCAL_ROOT=agent-definitions",
        // main application.yml binds these from LITELLM_BASE_URL / LITELLM_VIRTUAL_KEY (set in compose)
        "openjcockpit.agent-definitions.litellm.model-gateway-url=http://litellm:4000/v1",
        "openjcockpit.agent-definitions.litellm.model-gateway-key=sk-test"
})
@ActiveProfiles("local")
@AutoConfigureMockMvc
class A2ALocalStackProfileTest {

    @MockitoBean ModelProvider modelProvider;
    @MockitoBean AgentServiceClient agentService;
    @MockitoBean Ai ai;

    @Autowired MockMvc mockMvc;
    @Autowired ApplicationContext context;

    static final String SEND = """
            {"jsonrpc":"2.0","id":"1","method":"message/send","params":{"message":{"kind":"message","role":"user",
             "messageId":"m-1","parts":[{"kind":"text","text":"Write requirements for a password reset."}]}}}""";

    /** The rows of the agent service's local seed (db/seed-local): tenant local, agent-chat, PII guardrail, git tools. */
    @org.junit.jupiter.api.BeforeEach
    void localSeedBindings() {
        when(agentService.tenantBinding(anyString(), anyString())).thenReturn(java.util.Optional.empty());
        for (String agent : new String[]{"business-analyst", "solution-architect", "software-developer", "qa-engineer"}) {
            var tools = agent.equals("software-developer") || agent.equals("qa-engineer")
                    ? java.util.Set.of("git.list_files", "git.read_file", "git.write_file")
                    : java.util.Set.of("git.list_files", "git.read_file");
            when(agentService.tenantBinding("local", agent)).thenReturn(java.util.Optional.of(new AgentTenantBinding(
                    "local", agent, true, "agent-chat", null, false, false, tools, java.util.Set.of("pii-protection"))));
        }
    }

    @Test
    void profileRegistersGatewayModelsAndPointsAtTheAgentServiceAndItsOwnPublicAddress() {
        assertThat(context.containsBean("agentGatewayLlm-agent-chat")).isTrue();
        assertThat(context.containsBean("agentGatewayLlm-customer-chat")).isTrue();
        var env = context.getEnvironment();
        assertThat(env.getProperty("openjcockpit.agent-service.base-url")).isEqualTo("http://embabel-agent-service:8081");
        assertThat(env.getProperty("openjcockpit.agent-definitions.a2a.public-base-url"))
                .isEqualTo("http://embabel-agent-runtime:8082");
        assertThat(context.getBean(nl.metafactory.agentruntime.definition.binding.AgentTenantBindings.class))
                .isInstanceOf(nl.metafactory.agentruntime.definition.binding.RemoteAgentTenantBindings.class);
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
                    .andExpect(jsonPath("$.url").value("http://embabel-agent-runtime:8082/a2a/" + agent))
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
