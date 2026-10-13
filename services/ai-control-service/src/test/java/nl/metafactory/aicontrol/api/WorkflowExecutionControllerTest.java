package nl.metafactory.aicontrol.api;

import nl.metafactory.common.test.UnsecuredWebMvcTest;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.common.policy.PolicyDecisionAuditEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@UnsecuredWebMvcTest(WorkflowExecutionController.class)
class WorkflowExecutionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean EmbabelAgentClient client;

    @Test
    void decisionLogsReturnsEntriesFromClient() throws Exception {
        var entry = new PolicyDecisionAuditEntry("d-1", "wf-1", "run-1", null, "Noordzee Logistics", null, null,
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
