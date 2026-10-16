package nl.metafactory.agents.a2a;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.agent.api.tool.Tool;
import com.embabel.common.ai.model.ModelProvider;
import nl.metafactory.agents.approval.ApprovalDecisionAuditRepository;
import nl.metafactory.agents.approval.ApprovalDecisionService;
import nl.metafactory.agents.approval.ApprovalGateContextAssembler;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.definition.tool.RegisteredAgentTool;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A2A 0.3 JSON-RPC client contract against the local server: real security filter chain, real
 * resolver (local fixtures + H2 tenant bindings), Embabel {@link Ai} mocked so no live LLM is needed.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK, properties = {
        "openjcockpit.agent-definitions.enabled=true",
        "openjcockpit.agent-definitions.local-root=src/test/resources/agent-definitions",
        "openjcockpit.agent-definitions.a2a.enabled=true",
        "openjcockpit.agent-definitions.a2a.public-base-url=http://embabel-agent-service:8081",
        "embabel.models.llms.customer-chat=test-llm"
})
@AutoConfigureMockMvc
class A2AServerContractTest {

    @TestConfiguration
    static class Tools {
        @Bean
        RegisteredAgentTool customerRead() {
            return new RegisteredAgentTool("customer.read",
                    Tool.create("customer_read", "Read own account status", in -> Tool.Result.text("A")));
        }
    }

    @MockitoBean ModelProvider modelProvider;
    @MockitoBean AgentOrchestrator orchestrator;
    @MockitoBean ApprovalGateRegistry approvalGateRegistry;
    @MockitoBean ApprovalGateContextAssembler approvalGateContextAssembler;
    @MockitoBean ApprovalDecisionService approvalDecisionService;
    @MockitoBean ApprovalDecisionAuditRepository approvalDecisionAuditRepository;
    @MockitoBean nl.metafactory.agents.workflowtrigger.WorkflowTriggerCoordinator workflowTriggerCoordinator;
    @MockitoBean Ai ai;

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;

    PromptRunner runner;

    static final String SEND = """
            {"jsonrpc":"2.0","id":"req-1","method":"message/send","params":{"message":{"role":"user",
             "parts":[{"kind":"text","text":"Wat is de status van mijn account?"}],"messageId":"m-1","contextId":"ctx-1"}}}""";

    @BeforeEach
    void seed() {
        jdbc.sql("DELETE FROM agent_tenant_bindings").update();
        jdbc.sql("""
                INSERT INTO agent_tenant_bindings (tenant_id, agent_id, enabled, model_alias, pinned_version,
                  fallback_allowed, fallback_on_not_found, updated_at)
                VALUES ('tenant-a', 'customer-support', TRUE, NULL, NULL, FALSE, FALSE, ?)""")
                .params(OffsetDateTime.now()).update();
        jdbc.sql("INSERT INTO agent_tenant_binding_tools VALUES ('tenant-a','customer-support','customer.read')").update();
        runner = mock(PromptRunner.class, Answers.RETURNS_SELF);
        when(ai.withLlmByRole("customer-chat")).thenReturn(runner);
        when(runner.generateText(anyString())).thenReturn("Uw account is actief.");
    }

    private MockHttpServletRequestBuilder rpc(String body) {
        return post("/a2a/customer-support").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void unauthenticatedCallIsRejected() throws Exception {
        mockMvc.perform(rpc(SEND)).andExpect(status().isUnauthorized());
        verify(ai, never()).withLlmByRole(anyString());
    }

    @Test
    void messageSendReturnsAnA2AMessage() throws Exception {
        mockMvc.perform(rpc(SEND).header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "tenant-a").claim("preferred_username", "alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value("req-1"))
                .andExpect(jsonPath("$.result.kind").value("message"))
                .andExpect(jsonPath("$.result.role").value("agent"))
                .andExpect(jsonPath("$.result.contextId").value("ctx-1"))
                .andExpect(jsonPath("$.result.messageId").isString())
                .andExpect(jsonPath("$.result.parts[0].kind").value("text"))
                .andExpect(jsonPath("$.result.parts[0].text").value("Uw account is actief."))
                .andExpect(jsonPath("$.result.metadata.agentVersion").value("1.0.0"))
                .andExpect(jsonPath("$.result.metadata.traceId").value("4bf92f3577b34da6a3ce929d0e0e4736"))
                .andExpect(jsonPath("$.error").doesNotExist());
        verify(ai).withLlmByRole("customer-chat");
    }

    @Test
    void tokenWithoutTenantClaimIsNotPermitted() throws Exception {
        mockMvc.perform(rpc(SEND).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(A2AProtocol.INVALID_REQUEST))
                .andExpect(jsonPath("$.error.data.reason").value("TENANT_FORBIDDEN"));
        verify(ai, never()).withLlmByRole(anyString());
    }

    @Test
    void otherTenantCannotUseTheAgent() throws Exception {
        mockMvc.perform(rpc(SEND).with(jwt().jwt(j -> j.claim("tenant_id", "tenant-b"))))
                .andExpect(jsonPath("$.error.code").value(A2AProtocol.INVALID_REQUEST))
                .andExpect(jsonPath("$.error.data.reason").value("TENANT_FORBIDDEN"));
    }

    @Test
    void unsupportedMethodsAreRejectedExplicitly() throws Exception {
        var tenant = jwt().jwt(j -> j.claim("tenant_id", "tenant-a"));
        mockMvc.perform(rpc(SEND.replace("message/send", "message/stream")).with(tenant))
                .andExpect(jsonPath("$.error.code").value(A2AProtocol.UNSUPPORTED_OPERATION));
        mockMvc.perform(rpc("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tasks/get\",\"params\":{\"id\":\"t\"}}").with(tenant))
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.error.code").value(A2AProtocol.TASK_NOT_FOUND));
        mockMvc.perform(rpc("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"SendMessage\"}").with(tenant))
                .andExpect(jsonPath("$.error.code").value(A2AProtocol.METHOD_NOT_FOUND));
    }

    @Test
    void malformedRequestsGetJsonRpcErrors() throws Exception {
        var tenant = jwt().jwt(j -> j.claim("tenant_id", "tenant-a"));
        mockMvc.perform(rpc("{not json").with(tenant)).andExpect(jsonPath("$.error.code").value(A2AProtocol.PARSE_ERROR));
        mockMvc.perform(rpc(SEND.replace("\"2.0\"", "\"1.0\"")).with(tenant))
                .andExpect(jsonPath("$.error.code").value(A2AProtocol.INVALID_REQUEST));
        mockMvc.perform(rpc(SEND.replace("\"kind\":\"text\",\"text\":\"Wat is de status van mijn account?\"",
                        "\"kind\":\"file\",\"file\":{\"uri\":\"file:///etc/passwd\"}")).with(tenant))
                .andExpect(jsonPath("$.error.code").value(A2AProtocol.CONTENT_TYPE_NOT_SUPPORTED));
        mockMvc.perform(rpc(SEND.replace("\"role\":\"user\"", "\"role\":\"agent\"")).with(tenant))
                .andExpect(jsonPath("$.error.code").value(A2AProtocol.INVALID_PARAMS));
        verify(ai, never()).withLlmByRole(anyString());
    }
}
