package nl.metafactory.aicontrol.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.client.ApprovalGateConfigDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.PolicyDecisionAuditEntryDto;
import nl.metafactory.aicontrol.client.WorkflowDefinitionDto;
import nl.metafactory.aicontrol.client.WorkflowExportBundleDto;
import nl.metafactory.aicontrol.client.WorkflowGroupDto;
import nl.metafactory.aicontrol.client.WorkflowImportResultDto;
import nl.metafactory.aicontrol.client.WorkflowStartInputDto;
import nl.metafactory.aicontrol.client.WorkflowStartResponseDto;
import nl.metafactory.aicontrol.service.WorkflowStartEnrichmentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = WorkflowController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class WorkflowControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper objectMapper;
    @MockitoBean EmbabelAgentClient client;
    @MockitoBean WorkflowStartEnrichmentService startEnrichment;

    @org.junit.jupiter.api.BeforeEach
    void passThroughEnrichment() {
        when(startEnrichment.enrich(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private WorkflowDefinitionDto workflow(String id) {
        return new WorkflowDefinitionDto(id, "Onboarding", "Noordzee Logistics", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null,
                null, null);
    }

    @Test
    void listReturnsWorkflows() throws Exception {
        when(client.listWorkflows()).thenReturn(List.of(workflow("wf-1")));

        mockMvc.perform(get("/api/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("wf-1"));
    }

    @Test
    void exportReturnsBundleFromClient() throws Exception {
        var bundle = new WorkflowExportBundleDto(
                List.of(new WorkflowGroupDto("wg-1", "Onboarding flows", "desc", "Noordzee Logistics")),
                List.of(workflow("wf-1")));
        when(client.exportWorkflows()).thenReturn(bundle);

        mockMvc.perform(get("/api/workflows/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].id").value("wg-1"))
                .andExpect(jsonPath("$.workflows[0].id").value("wf-1"));
    }

    @Test
    void importForwardsBundleAndReturnsCounts() throws Exception {
        var bundle = new WorkflowExportBundleDto(
                List.of(new WorkflowGroupDto("wg-1", "Onboarding flows", "desc", "Noordzee Logistics")),
                List.of(workflow("wf-1")));
        when(client.importWorkflows(any())).thenReturn(new WorkflowImportResultDto(1, 1,
                List.of("workflow orb references unknown workflow 'wf-missing'")));

        mockMvc.perform(post("/api/workflows/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bundle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupsImported").value(1))
                .andExpect(jsonPath("$.workflowsImported").value(1))
                .andExpect(jsonPath("$.violations[0]").value("workflow orb references unknown workflow 'wf-missing'"));

        verify(client).importWorkflows(any());
    }

    @Test
    void getReturnsWorkflow() throws Exception {
        when(client.getWorkflow("wf-1")).thenReturn(Optional.of(workflow("wf-1")));

        mockMvc.perform(get("/api/workflows/wf-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Onboarding"));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(client.getWorkflow("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/workflows/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturns201() throws Exception {
        when(client.createWorkflow(any())).thenReturn(workflow("wf-1"));

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(workflow("wf-1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("wf-1"));
    }

    @Test
    void updateReturnsUpdatedWorkflow() throws Exception {
        when(client.updateWorkflow(any(), any())).thenReturn(workflow("wf-1"));

        mockMvc.perform(put("/api/workflows/wf-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(workflow("wf-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("wf-1"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/workflows/wf-1"))
                .andExpect(status().isNoContent());

        verify(client).deleteWorkflow("wf-1");
    }

    @Test
    void startReturnsExecutionResponse() throws Exception {
        when(client.startWorkflow(org.mockito.ArgumentMatchers.eq("wf-1"), any())).thenReturn(
                new WorkflowStartResponseDto("wf-1", "run-1", "RUNNING", Instant.now(), "started"));

        // The generated WorkflowApi declares consumes=application/json for the optional
        // request body (unaffected by required=false); the real dashboard client always
        // sends this header (see workflowApi.ts), so a JSON content type with an empty
        // body is the faithful equivalent of "no start input supplied".
        mockMvc.perform(post("/api/workflows/wf-1/start").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value("run-1"));

        verify(client).startWorkflow("wf-1", WorkflowStartInputDto.empty());
    }

    @Test
    void startForwardsPromptAndSpecFileToClient() throws Exception {
        var input = new WorkflowStartInputDto("Create a spec for login", "001-example-feature/spec.md",
                "https://github.com/org/repo.git", "11111111-2222-3333-4444-555555555555", null, null, null);
        when(client.startWorkflow(org.mockito.ArgumentMatchers.eq("wf-1"), any())).thenReturn(
                new WorkflowStartResponseDto("wf-1", "run-1", "RUNNING", Instant.now(), "started"));

        mockMvc.perform(post("/api/workflows/wf-1/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk());

        verify(client).startWorkflow("wf-1", input);
        verify(startEnrichment).enrich(input);
    }

    // ── MADP-54 Q3 / AC-11 / AC-05 : caller-supplied baseBranch validation at the boundary ──

    @Test
    void startRejectsMalformedCallerBaseBranchWith400AndMakesNoDownstreamCall() throws Exception {
        mockMvc.perform(post("/api/workflows/wf-1/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"11111111-2222-3333-4444-555555555555\",\"baseBranch\":\"my branch\"}"))
                .andExpect(status().isBadRequest());

        verify(startEnrichment, org.mockito.Mockito.never()).enrich(any());
        verify(client, org.mockito.Mockito.never()).startWorkflow(any(), any());
    }

    @Test
    void startAcceptsBodyWithoutBaseBranchPropertyWithout400() throws Exception {
        when(client.startWorkflow(org.mockito.ArgumentMatchers.eq("wf-1"), any())).thenReturn(
                new WorkflowStartResponseDto("wf-1", "run-1", "RUNNING", Instant.now(), "started"));

        mockMvc.perform(post("/api/workflows/wf-1/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Create a spec\"}"))
                .andExpect(status().isOk());

        verify(client).startWorkflow(org.mockito.ArgumentMatchers.eq("wf-1"), any());
    }

    @Test
    void startTrimsAcceptableCallerBaseBranchBeforeEnrichment() throws Exception {
        when(client.startWorkflow(org.mockito.ArgumentMatchers.eq("wf-1"), any())).thenReturn(
                new WorkflowStartResponseDto("wf-1", "run-1", "RUNNING", Instant.now(), "started"));

        mockMvc.perform(post("/api/workflows/wf-1/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseBranch\":\"  develop  \"}"))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(WorkflowStartInputDto.class);
        verify(startEnrichment).enrich(captor.capture());
        assertThat(captor.getValue().baseBranch()).isEqualTo("develop");
    }

    @Test
    void decisionLogsReturnsEntriesFromClient() throws Exception {
        var entry = new PolicyDecisionAuditEntryDto("d-1", "wf-1", "run-1", null, "Noordzee Logistics", null, null,
                "requirement", null, null, null, "dashboard-button", "agent.use", "opa-decision-1", "ALLOWED",
                "allowed by policy", "low", false, List.of("agent.use"), Instant.now(), "v1", false, null);
        when(client.getDecisionLogsForWorkflow("wf-1", null, null, null, null, null, null, null))
                .thenReturn(List.of(entry));

        mockMvc.perform(get("/api/workflows/wf-1/decision-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("d-1"));
    }

    // ── F1 (workflow-approval-gate architecture §3.1) ───────────────────────────
    // WorkflowDefinitionDto is hand-written and relayed verbatim by this controller; if
    // approvalGate were ever dropped from that record, a gate configured in the dashboard would be
    // silently discarded here with a 200 response and no log line. These are the only tests that
    // would catch that regression — the antrun schema-mapping guard does not (it only catches a
    // missing mapping *pair*, never a missing *field* on an already-mapped hand-written record).

    private WorkflowDefinitionDto workflowWithGate(String id, ApprovalGateConfigDto gate) {
        return new WorkflowDefinitionDto(id, "Onboarding", "Noordzee Logistics", null, "desc",
                List.of("requirement", "realisation"), List.of(), List.of(), List.of(), null, null,
                false, null, "ACTIVE", null, null, gate, null);
    }

    @Test
    void createRelaysApprovalGateToTheClient() throws Exception {
        var gate = new ApprovalGateConfigDto(true, "realisation");
        var withGate = workflowWithGate("wf-1", gate);
        when(client.createWorkflow(any())).thenReturn(withGate);

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withGate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approvalGate.enabled").value(true))
                .andExpect(jsonPath("$.approvalGate.placementStage").value("realisation"));

        var captor = ArgumentCaptor.forClass(WorkflowDefinitionDto.class);
        verify(client).createWorkflow(captor.capture());
        assertThat(captor.getValue().approvalGate()).isEqualTo(gate);
    }

    @Test
    void updateRelaysApprovalGateToTheClientInBothDirections() throws Exception {
        var gate = new ApprovalGateConfigDto(true, "impact");
        var withGate = workflowWithGate("wf-1", gate);
        when(client.updateWorkflow(any(), any())).thenReturn(withGate);

        mockMvc.perform(put("/api/workflows/wf-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withGate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalGate.enabled").value(true))
                .andExpect(jsonPath("$.approvalGate.placementStage").value("impact"));

        var captor = ArgumentCaptor.forClass(WorkflowDefinitionDto.class);
        verify(client).updateWorkflow(org.mockito.ArgumentMatchers.eq("wf-1"), captor.capture());
        assertThat(captor.getValue().approvalGate()).isEqualTo(gate);
    }

    @Test
    void createWithoutApprovalGateRelaysNullRatherThanDroppingTheField() throws Exception {
        var withoutGate = workflowWithGate("wf-2", null);
        when(client.createWorkflow(any())).thenReturn(withoutGate);

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withoutGate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approvalGate").doesNotExist());

        var captor = ArgumentCaptor.forClass(WorkflowDefinitionDto.class);
        verify(client).createWorkflow(captor.capture());
        assertThat(captor.getValue().approvalGate()).isNull();
    }
}
