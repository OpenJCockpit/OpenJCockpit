package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.client.AgentRunDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.WorkflowExecutionPageDto;
import nl.metafactory.aicontrol.client.WorkflowExecutionSummaryDto;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = WorkflowExecutionController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class WorkflowExecutionControllerHistoryTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean EmbabelAgentClient client;

    private WorkflowExecutionSummaryDto summary(String runId) {
        return new WorkflowExecutionSummaryDto(runId, "wf-1", "COMPLETED",
                Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T00:05:00Z"), 300000L, "alice");
    }

    @Test
    void listWorkflowExecutionsReturnsPageFromClient() throws Exception {
        var page = new WorkflowExecutionPageDto(List.of(summary("run-1")), 10, 5, 1, false);
        when(client.listWorkflowExecutionsOrThrow("wf-1", 10, 5)).thenReturn(page);

        mockMvc.perform(get("/api/workflows/wf-1/executions?limit=10&offset=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].runId").value("run-1"))
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.offset").value(5))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void listWorkflowExecutionsUsesDefaultLimitAndOffsetWhenOmitted() throws Exception {
        var page = new WorkflowExecutionPageDto(List.of(), 20, 0, 0, false);
        when(client.listWorkflowExecutionsOrThrow("wf-1", 20, 0)).thenReturn(page);

        mockMvc.perform(get("/api/workflows/wf-1/executions"))
                .andExpect(status().isOk());

        verify(client).listWorkflowExecutionsOrThrow("wf-1", 20, 0);
    }

    @Test
    void listWorkflowExecutionsRejectsLimitBelowMinimum() throws Exception {
        mockMvc.perform(get("/api/workflows/wf-1/executions?limit=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("limit")));
    }

    @Test
    void listWorkflowExecutionsRejectsLimitAboveMaximum() throws Exception {
        mockMvc.perform(get("/api/workflows/wf-1/executions?limit=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("limit")));
    }

    @Test
    void listWorkflowExecutionsRejectsNegativeOffset() throws Exception {
        mockMvc.perform(get("/api/workflows/wf-1/executions?offset=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("offset")));
    }

    @Test
    void getWorkflowExecutionReturnsRunFromClient() throws Exception {
        var run = new AgentRunDto("run-1", "cust-1", "spec.md", "https://github.com/org/repo",
                "COMPLETED", Instant.parse("2024-01-01T00:00:00Z"), List.of(), List.of(),
                "wf-1", "alice", Instant.parse("2024-01-01T00:05:00Z"), null);
        when(client.getWorkflowExecutionOrThrow("wf-1", "run-1")).thenReturn(run);

        mockMvc.perform(get("/api/workflows/wf-1/executions/run-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("run-1"))
                .andExpect(jsonPath("$.workflowId").value("wf-1"))
                .andExpect(jsonPath("$.startedBy").value("alice"));
    }
}
