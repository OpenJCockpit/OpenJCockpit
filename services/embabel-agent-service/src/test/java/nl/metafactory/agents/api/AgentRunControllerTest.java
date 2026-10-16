package nl.metafactory.agents.api;

import nl.metafactory.common.test.UnsecuredWebMvcTest;
import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@UnsecuredWebMvcTest(AgentRunController.class)
class AgentRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean nl.metafactory.agents.orchestration.AgentRunOutputs agentRunOutputs;

    @MockitoBean
    private AgentOrchestrator orchestrator;

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

    // Runs start only through a workflow (POST /api/workflows/{id}/start); there is no direct
    // agent-run start and no built-in agent listing any more.
    @Test
    void directAgentRunStartAndBuiltInAgentListingNoLongerExist() throws Exception {
        mockMvc.perform(post("/api/agent-runs").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/agents"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsTheFullAgentOutputsOfARun() throws Exception {
        org.mockito.Mockito.when(agentRunOutputs.find("run-9")).thenReturn(java.util.List.of(
                new nl.metafactory.agents.orchestration.AgentRunOutputs.Output("a2a:litellm:qa-engineer", 0, "verified")));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/agent-runs/run-9/outputs"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].agentId").value("a2a:litellm:qa-engineer"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].output").value("verified"));
    }
}
