package nl.metafactory.agents.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.agents.model.AgentDefinition;
import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AgentRunController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class AgentRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private AgentOrchestrator orchestrator;

    @Test
    void getAgentsReturnsAvailableAgents() throws Exception {
        when(orchestrator.availableAgents()).thenReturn(List.of(
                new AgentDefinition("req", "Requirement Agent", "Extract requirements", "specification",
                        List.of("spec.read"), List.of("requirements.yaml"), 0, "SpecContent", "RequirementAnalysis")
        ));

        mockMvc.perform(get("/api/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("req"))
                .andExpect(jsonPath("$[0].name").value("Requirement Agent"));
    }

    @Test
    void getAgentRunReturnsRunById() throws Exception {
        var run = new AgentRun("run-42", "cust2", "other.md", "", "COMPLETED", Instant.now(), List.of(), List.of(), null, null, null, null);
        when(orchestrator.get("run-42")).thenReturn(run);

        mockMvc.perform(get("/api/agent-runs/run-42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("run-42"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void deleteAgentRunStopsTheRun() throws Exception {
        mockMvc.perform(delete("/api/agent-runs/run-1"))
                .andExpect(status().isNoContent());

        verify(orchestrator).stop("run-1");
    }

    // ── AC-42 server half (workflow-approval-gate, V8) ──────────────────────────
    // This controller is a thin pass-through over AgentOrchestrator.get(...), so it needs no
    // special-casing for an unknown run id: whatever status the orchestrator returns (here,
    // RUN_STATE_LOST — see EmbabelOrchestratorRunStateLostTest) is served at HTTP 200, exactly
    // like any other status. AgentRunDto.status stays a free-form string (V7), so this is
    // additive, not a contract change.

    @Test
    void getAgentRunReturns200WithRunStateLostForAnUnknownRun() throws Exception {
        var lost = new AgentRun("unknown-run", "unknown", "unknown", "", "RUN_STATE_LOST",
                Instant.now(), List.of(), List.of(), null, null, null, null);
        when(orchestrator.get("unknown-run")).thenReturn(lost);

        mockMvc.perform(get("/api/agent-runs/unknown-run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUN_STATE_LOST"));
    }

    // ── AC-11 / AC-32 / AC-33: HTTP response body carries the sanitised failureSummary ──
    // The controller is a thin pass-through over AgentOrchestrator.get(...); Spring serialises the
    // AgentRun record directly, so the JSON field name is the record component name `failureSummary`.
    // This test proves the transport/serialization path: a FAILED run's response body contains the
    // correct sanitised failureSummary value and does NOT contain an independently-fabricated raw
    // sensitive marker string (defense-in-depth; the sanitiser itself is covered by
    // RunFailureDiagnosticsTest).

    @Test
    void getAgentRunReturnsFailureSummaryInResponseBodyForAFailedRun() throws Exception {
        String sanitisedSummary = "IllegalStateException <- (definition.yaml: file not found)";
        // Independently-fabricated raw/sensitive marker that must never leak into the response body.
        // This string is NOT part of sanitisedSummary; it is used only for the negative assertion.
        String rawSensitiveMarker = "/etc/secret-config";

        var run = new AgentRun("run-failed", "cust1", "spec.md", "", "FAILED",
                Instant.now(), List.of(), List.of(), "wf-1", "alice", Instant.now(), sanitisedSummary);
        when(orchestrator.get("run-failed")).thenReturn(run);

        var result = mockMvc.perform(get("/api/agent-runs/run-failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureSummary").value(sanitisedSummary))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(responseBody)
                .doesNotContain(rawSensitiveMarker);
    }

    // AC-13: a COMPLETED run's response body must not carry a failureSummary value.
    @Test
    void getAgentRunCompletedRunHasNoFailureSummary() throws Exception {
        var run = new AgentRun("run-completed", "cust1", "spec.md", "", "COMPLETED",
                Instant.now(), List.of(), List.of(), "wf-1", "alice", Instant.now(), null);
        when(orchestrator.get("run-completed")).thenReturn(run);

        mockMvc.perform(get("/api/agent-runs/run-completed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.failureSummary").doesNotExist());
    }

    @Test
    void postAgentRunReturns400WhenAgentIdsIsEmpty() throws Exception {
        var request = new AgentRunRequest("cust1", "spec.md", List.of(), "user", "https://github.com/org/repo", null, null, null, null, null, null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        mockMvc.perform(post("/api/agent-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postAgentRunWithNonEmptyAgentIdsReturns202WithRun() throws Exception {
        var request = new AgentRunRequest("cust1", "spec.md", List.of("requirement"), "user", "https://github.com/org/repo", null, null, null, null, null, null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var run = new AgentRun("run-1", "cust1", "spec.md", "https://github.com/org/repo", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null);
        when(orchestrator.start(any())).thenReturn(run);
        mockMvc.perform(post("/api/agent-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").value("run-1"))
                .andExpect(jsonPath("$.repositoryUrl").value("https://github.com/org/repo"))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    // Security: POST /api/agent-runs is not workflow-scoped and must never let a caller forge
    // history attribution (startedBy) or inject a run into another workflow's history
    // (workflowId) by supplying those fields directly on the request body.
    @Test
    void postAgentRunStripsClientSuppliedWorkflowIdAndStartedBy() throws Exception {
        var request = new AgentRunRequest("cust1", "spec.md", List.of("requirement"), "user",
                "https://github.com/org/repo", null, null, null, null, "forged-workflow-id", "forged-user", null, null);
        var run = new AgentRun("run-1", "cust1", "spec.md", "https://github.com/org/repo", "RUNNING",
                Instant.now(), List.of(), List.of(), null, null, null, null);
        var captor = org.mockito.ArgumentCaptor.forClass(AgentRunRequest.class);
        when(orchestrator.start(captor.capture())).thenReturn(run);

        mockMvc.perform(post("/api/agent-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        org.assertj.core.api.Assertions.assertThat(captor.getValue().workflowId()).isNull();
        org.assertj.core.api.Assertions.assertThat(captor.getValue().startedBy()).isNull();
        org.assertj.core.api.Assertions.assertThat(captor.getValue().initiator()).isNull();
        org.assertj.core.api.Assertions.assertThat(captor.getValue().chainAncestry()).isNull();
        org.assertj.core.api.Assertions.assertThat(captor.getValue().customerId()).isEqualTo("cust1");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().requestedBy()).isEqualTo("user");
    }
}
