package nl.metafactory.agents.config;

import com.embabel.common.ai.model.ModelProvider;
import nl.metafactory.agents.approval.ApprovalDecisionAuditRepository;
import nl.metafactory.agents.approval.ApprovalDecisionService;
import nl.metafactory.agents.approval.ApprovalGateContextAssembler;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.model.AgentDefinition;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SecurityConfigTest {

    @MockitoBean ModelProvider modelProvider;

    @Autowired MockMvc mockMvc;
    @MockitoBean AgentOrchestrator orchestrator;
    @MockitoBean ApprovalGateRegistry approvalGateRegistry;
    @MockitoBean ApprovalGateContextAssembler approvalGateContextAssembler;
    @MockitoBean ApprovalDecisionService approvalDecisionService;
    @MockitoBean ApprovalDecisionAuditRepository approvalDecisionAuditRepository;
    @MockitoBean nl.metafactory.agents.workflowtrigger.WorkflowTriggerCoordinator workflowTriggerCoordinator;

    @BeforeEach
    void setUp() {
        when(orchestrator.availableAgents()).thenReturn(List.of(
                new AgentDefinition("req", "Requirement Agent", "Extract requirements",
                        "specification", List.of(), List.of(), 0, "SpecContent", "RequirementAnalysis")
        ));
    }

    @Test
    void unauthenticatedRequestIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/agents"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestWithJwtIsAllowed() throws Exception {
        mockMvc.perform(get("/api/agents").with(jwt()))
               .andExpect(status().isOk());
    }

    @Test
    void actuatorHealthIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
               .andExpect(status().isOk());
    }

    // ── AC-37/AC-38 (workflow-approval-gate) ────────────────────────────────────

    @Test
    void approvalGateContextWithoutAuthenticationIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/agent-runs/run-1/approval-gate"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void approvalDecisionsWithoutAuthenticationIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/agent-runs/run-1/approval-decisions"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void anAuthenticatedUserWithNoRealmRolesCanReadApprovalDecisions() throws Exception {
        // AC-38: this platform defines no realm roles; every endpoint is "authenticated only".
        // Pinned deliberately so a future role introduction is a visible, tested change.
        when(approvalDecisionAuditRepository.findByRunId("run-1")).thenReturn(List.of());

        mockMvc.perform(get("/api/agent-runs/run-1/approval-decisions").with(jwt()))
               .andExpect(status().isOk());
    }

    @Test
    void wellKnownOauthProtectedResourceMetadataIsNotAnonymous() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource"))
               .andExpect(status().isUnauthorized());
    }

    // ── workflow execution history ──────────────────────────────────────────────

    @Test
    void workflowExecutionHistoryWithoutAuthenticationIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/workflows/wf-1/executions"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void workflowExecutionDetailWithoutAuthenticationIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/workflows/wf-1/executions/run-1"))
               .andExpect(status().isUnauthorized());
    }
}
