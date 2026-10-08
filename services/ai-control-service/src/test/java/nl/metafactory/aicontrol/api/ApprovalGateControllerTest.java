package nl.metafactory.aicontrol.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.client.ApprovalDecisionAuditEntryDto;
import nl.metafactory.aicontrol.client.ApprovalDecisionKind;
import nl.metafactory.aicontrol.client.ApprovalDecisionRequestDto;
import nl.metafactory.aicontrol.client.ApprovalDecisionResultDto;
import nl.metafactory.aicontrol.client.ApprovalGateContextDto;
import nl.metafactory.aicontrol.client.ApprovalStageOutcome;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ApprovalGateController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class ApprovalGateControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper objectMapper;
    @MockitoBean EmbabelAgentClient client;

    private ApprovalGateContextDto context() {
        return new ApprovalGateContextDto("run-1", "wf-1", "realisation", ApprovalStageOutcome.PUBLISHED,
                null, "Validation added", List.of("src/App.java"), 1, 0,
                "feat/wf-1-run", "https://github.com/org/repo/pull/9", null,
                1, 3, true, true, List.of(), Instant.now());
    }

    @Test
    void getApprovalGateContextReturnsContext() throws Exception {
        when(client.getApprovalGateContext("run-1")).thenReturn(Optional.of(context()));

        mockMvc.perform(get("/api/agent-runs/run-1/approval-gate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("run-1"))
                .andExpect(jsonPath("$.feedbackSupported").value(true));
    }

    @Test
    void getApprovalGateContextReturns404WhenNoGateOpen() throws Exception {
        when(client.getApprovalGateContext("run-1")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/agent-runs/run-1/approval-gate"))
                .andExpect(status().isNotFound());
    }

    @Test
    void submitApprovalDecisionReturnsResultFromClient() throws Exception {
        var request = new ApprovalDecisionRequestDto(ApprovalDecisionKind.ACCEPT, 1, null);
        when(client.submitApprovalDecision(eq("run-1"), any()))
                .thenReturn(new ApprovalDecisionResultDto("run-1", "RUNNING", 1, "Accepted"));

        mockMvc.perform(post("/api/agent-runs/run-1/approval-gate/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void submitApprovalDecisionRelays400FromClient() throws Exception {
        var request = new ApprovalDecisionRequestDto(ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 1, "feedback");
        when(client.submitApprovalDecision(eq("run-1"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Feedback is not supported for placement stage impact"));

        mockMvc.perform(post("/api/agent-runs/run-1/approval-gate/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitApprovalDecisionRelays404FromClient() throws Exception {
        var request = new ApprovalDecisionRequestDto(ApprovalDecisionKind.ACCEPT, 1, null);
        when(client.submitApprovalDecision(eq("missing"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found: missing"));

        mockMvc.perform(post("/api/agent-runs/missing/approval-gate/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void submitApprovalDecisionRelays409FromClient() throws Exception {
        var request = new ApprovalDecisionRequestDto(ApprovalDecisionKind.ACCEPT, 1, null);
        when(client.submitApprovalDecision(eq("run-1"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                        "Decision already recorded for iteration 1"));

        mockMvc.perform(post("/api/agent-runs/run-1/approval-gate/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void listApprovalDecisionsReturnsAuditTrail() throws Exception {
        var entry = new ApprovalDecisionAuditEntryDto("d-1", "run-1", "wf-1", "realisation", 1,
                ApprovalDecisionKind.ACCEPT, null, "alice", "sub-1", ApprovalStageOutcome.PUBLISHED,
                "feat/wf-1-run", "https://github.com/org/repo/pull/9", Instant.now());
        when(client.listApprovalDecisions("run-1")).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/agent-runs/run-1/approval-decisions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("d-1"))
                .andExpect(jsonPath("$[0].actorUsername").value("alice"));
    }

}
