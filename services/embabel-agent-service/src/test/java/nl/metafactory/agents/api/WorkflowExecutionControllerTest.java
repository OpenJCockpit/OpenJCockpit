package nl.metafactory.agents.api;

import nl.metafactory.common.test.UnsecuredWebMvcTest;
import nl.metafactory.agents.policy.PolicyDecisionLogExportService;
import nl.metafactory.agents.policy.model.DecisionLogFilter;
import nl.metafactory.common.policy.PolicyDecisionAuditEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@UnsecuredWebMvcTest(WorkflowExecutionController.class)
class WorkflowExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicyDecisionLogExportService decisionLogExportService;

    @Test
    void decisionLogsReturnsExportedEntries() throws Exception {
        var entry = new PolicyDecisionAuditEntry("d-1", "wf-1", "run-1", null, "Noordzee Logistics", null, null,
                "requirement", null, null, null, "dashboard-button", "agent.use", "opa-decision-1", "DENIED",
                "denied by policy", "high", false, List.of("agent.use"), Instant.now(), "v1", false, null);
        when(decisionLogExportService.exportDecisionLogsForWorkflowExecution(any(), any())).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/workflow-executions/run-1/decision-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("d-1"))
                .andExpect(jsonPath("$[0].opaDecisionResult").value("DENIED"));
    }

    @Test
    void decisionLogsBindsQueryParametersIntoFilter() throws Exception {
        when(decisionLogExportService.exportDecisionLogsForWorkflowExecution(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/workflow-executions/run-1/decision-logs")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-02-01T00:00:00Z")
                        .param("agentId", "a-1")
                        .param("subagentId", "s-1")
                        .param("skillId", "k-1")
                        .param("mcpToolName", "git_push")
                        .param("result", "DENIED"))
                .andExpect(status().isOk());

        verify(decisionLogExportService).exportDecisionLogsForWorkflowExecution("run-1",
                new DecisionLogFilter(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z"),
                        "a-1", "s-1", "k-1", "git_push", "DENIED"));
    }
}
