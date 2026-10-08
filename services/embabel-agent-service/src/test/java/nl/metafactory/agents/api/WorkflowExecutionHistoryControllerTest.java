package nl.metafactory.agents.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.workflow.WorkflowExecutionHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = WorkflowExecutionHistoryController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class WorkflowExecutionHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private WorkflowExecutionHistoryService historyService;

    @Test
    void listDelegatesToServiceAndReturnsThePageEnvelope() throws Exception {
        var summary = new WorkflowExecutionHistoryService.WorkflowExecutionSummary(
                "run-1", "wf-1", "COMPLETED", Instant.now(), Instant.now(), 1000L, "alice");
        var page = new WorkflowExecutionHistoryService.WorkflowExecutionHistoryPage(
                List.of(summary), 20, 0, 1, false);
        when(historyService.list("wf-1", null, null)).thenReturn(page);

        mockMvc.perform(get("/api/workflows/wf-1/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].runId").value("run-1"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void listPassesLimitAndOffsetQueryParamsThrough() throws Exception {
        var page = new WorkflowExecutionHistoryService.WorkflowExecutionHistoryPage(List.of(), 5, 10, 0, false);
        when(historyService.list("wf-1", 5, 10)).thenReturn(page);

        mockMvc.perform(get("/api/workflows/wf-1/executions").param("limit", "5").param("offset", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(5))
                .andExpect(jsonPath("$.offset").value(10));

        verify(historyService).list(eq("wf-1"), eq(5), eq(10));
    }

    @Test
    void listReturns404WhenServiceReportsWorkflowNotFound() throws Exception {
        when(historyService.list(eq("missing"), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found: missing"));

        mockMvc.perform(get("/api/workflows/missing/executions"))
                .andExpect(status().isNotFound());
    }

    @Test
    void detailDelegatesToServiceAndReturnsTheFullAgentRun() throws Exception {
        var run = new AgentRun("run-1", "cust1", "spec.md", "https://github.com/org/repo", "RUNNING",
                Instant.now(), List.of(), List.of(), "wf-1", "alice", null, null);
        when(historyService.detail("wf-1", "run-1")).thenReturn(run);

        mockMvc.perform(get("/api/workflows/wf-1/executions/run-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("run-1"))
                .andExpect(jsonPath("$.workflowId").value("wf-1"));
    }

    @Test
    void detailReturns404WhenServiceReportsExecutionNotFound() throws Exception {
        when(historyService.detail("wf-1", "unknown-run"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution not found"));

        mockMvc.perform(get("/api/workflows/wf-1/executions/unknown-run"))
                .andExpect(status().isNotFound());
    }
}
