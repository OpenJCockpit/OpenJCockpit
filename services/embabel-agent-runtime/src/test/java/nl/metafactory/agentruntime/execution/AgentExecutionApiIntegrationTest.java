package nl.metafactory.agentruntime.execution;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.common.ai.model.ModelProvider;
import nl.metafactory.agentruntime.management.AgentServiceClient;
import nl.metafactory.common.agentruntime.AgentTenantBinding;
import nl.metafactory.common.agentruntime.WorkflowRunDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The execution boundary as the agent service uses it: real security chain and resolver, a catalog agent
 * sent as snapshot, the tenant binding and the run scope served by a (mocked) agent service, the LLM mocked.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK, properties = {
        "openjcockpit.agent-definitions.enabled=true",
        "openjcockpit.agent-definitions.local-root=src/test/resources/agent-definitions",
        "embabel.models.llms.agent-chat=test-llm"
})
@AutoConfigureMockMvc
class AgentExecutionApiIntegrationTest {

    static final String MARKDOWN = "---\\nid: triage\\nversion: \\\"3\\\"\\nmodel: agent-chat\\n---\\n# Role\\nTriage issues.\\n";

    @MockitoBean ModelProvider modelProvider;
    @MockitoBean AgentServiceClient agentService;
    @MockitoBean Ai ai;

    @Autowired MockMvc mockMvc;

    PromptRunner runner;

    @BeforeEach
    void setUp() {
        when(agentService.tenantBinding(anyString(), anyString())).thenReturn(Optional.empty());
        when(agentService.tenantBinding("local", "triage")).thenReturn(Optional.of(
                new AgentTenantBinding("local", "triage", true, null, null, false, false, Set.of(), Set.of())));
        when(agentService.workflowRun("run-1")).thenReturn(Optional.of(new WorkflowRunDescriptor("wf-1", "run-1", null)));
        runner = mock(PromptRunner.class, Answers.RETURNS_SELF);
        when(ai.withLlmByRole("agent-chat")).thenReturn(runner);
        when(runner.generateText(anyString())).thenReturn("Severity: high");
    }

    private static String body(String executionId, String tenant) {
        return """
                {"executionId":"%s","tenantId":"%s","agentId":"triage","principal":"alice","workflowRunId":"run-1",
                 "message":"Login is broken","definition":{"status":"FOUND","markdown":"%s"}}""".formatted(executionId, tenant, MARKDOWN);
    }

    private MockHttpServletRequestBuilder execute(String body) {
        return post("/internal/v1/agent-executions").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void theAgentServiceRunsACatalogAgentOnceAndCanReadItsStatus() throws Exception {
        var service = jwt().jwt(j -> j.claim("azp", "openjcockpit-agent-service"));

        mockMvc.perform(execute(body("run-1:e-1", "local")).with(service))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value("run-1:e-1"))
                .andExpect(jsonPath("$.agentId").value("triage"))
                .andExpect(jsonPath("$.version").value("3"))
                .andExpect(jsonPath("$.text").value("Severity: high"));
        mockMvc.perform(execute(body("run-1:e-1", "local")).with(service))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("DUPLICATE_EXECUTION"));
        mockMvc.perform(get("/internal/v1/agent-executions/run-1:e-1").with(service))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"));

        verify(runner, times(1)).generateText(anyString());
        verify(agentService).workflowRun("run-1");
    }

    @Test
    void tenantIsolationHoldsAcrossTheBoundary() throws Exception {
        mockMvc.perform(execute(body("e-2", "other-tenant")).with(jwt().jwt(j -> j.claim("azp", "openjcockpit-agent-service"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.reason").value("TENANT_FORBIDDEN"));
        verify(ai, never()).withLlmByRole(anyString());
    }

    @Test
    void probesNeedNoTokenButEverythingElseDoes() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void nobodyButTheAgentServiceGetsIn() throws Exception {
        mockMvc.perform(execute(body("e-3", "local"))).andExpect(status().isUnauthorized());
        mockMvc.perform(execute(body("e-3", "local")).with(jwt().jwt(j -> j.claim("azp", "openjcockpit-a2a")
                        .claim("tenant_id", "local"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("FORBIDDEN"));
        mockMvc.perform(post("/internal/v1/definition-drafts/agent").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"concept\":\"x\"}").with(jwt()))
                .andExpect(status().isForbidden());
        verify(ai, never()).withLlmByRole(anyString());
    }
}
