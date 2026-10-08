package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.PolicyDecisionAuditEntryDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = WorkflowExecutionController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class WorkflowExecutionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean EmbabelAgentClient client;

    @Test
    void decisionLogsReturnsEntriesFromClient() throws Exception {
        var entry = new PolicyDecisionAuditEntryDto("d-1", "wf-1", "run-1", null, "Noordzee Logistics", null, null,
                "requirement", null, null, null, "dashboard-button", "agent.use", "opa-decision-1", "DENIED",
                "denied by policy", "high", false, List.of("agent.use"), Instant.now(), "v1", false, null);
        when(client.getDecisionLogsForWorkflowExecution("run-1", null, null, null, null, null, null, null))
                .thenReturn(List.of(entry));

        mockMvc.perform(get("/api/workflow-executions/run-1/decision-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("d-1"))
                .andExpect(jsonPath("$[0].opaDecisionResult").value("DENIED"));
    }
}
