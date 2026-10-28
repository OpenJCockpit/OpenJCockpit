package nl.metafactory.agents.config;

import nl.metafactory.agents.approval.ApprovalDecisionAuditRepository;
import nl.metafactory.agents.approval.ApprovalDecisionService;
import nl.metafactory.agents.approval.ApprovalGateContextAssembler;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SecurityConfigTest {


    @Autowired MockMvc mockMvc;
    @MockitoBean AgentOrchestrator orchestrator;
    @MockitoBean ApprovalGateRegistry approvalGateRegistry;
    @MockitoBean ApprovalGateContextAssembler approvalGateContextAssembler;
    @MockitoBean ApprovalDecisionService approvalDecisionService;
    @MockitoBean ApprovalDecisionAuditRepository approvalDecisionAuditRepository;
    @MockitoBean nl.metafactory.agents.workflowtrigger.WorkflowTriggerCoordinator workflowTriggerCoordinator;

    @Test
    void unauthenticatedRequestIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/agent-definitions"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestWithJwtIsAllowed() throws Exception {
        mockMvc.perform(get("/api/agent-definitions").with(jwt()))
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
        // Reads stay "authenticated only" in this service; the dashboard API (ai-control-service)
        // requires openjcockpit-viewer for them. Only mutations are re-checked by role here.
        when(approvalDecisionAuditRepository.findByRunId("run-1")).thenReturn(List.of());

        mockMvc.perform(get("/api/agent-runs/run-1/approval-decisions").with(jwt()))
               .andExpect(status().isOk());
    }

    @Test
    void anAuthenticatedUserWithoutTheApproverRoleCannotDecideAGate() throws Exception {
        mockMvc.perform(post("/api/agent-runs/run-1/approval-gate/decision")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_openjcockpit-operator")))
                        .contentType("application/json").content("{\"decision\":\"APPROVE\"}"))
               .andExpect(status().isForbidden());

        verifyNoInteractions(approvalDecisionService);
    }

    @Test
    void internalGitWorkspaceTicketsRejectAHumanTokenEvenForAnAdmin() throws Exception {
        mockMvc.perform(post("/internal/git-workspace-tickets")
                        .with(jwt().jwt(j -> j.claim("azp", "openjcockpit"))
                                .authorities(new SimpleGrantedAuthority("ROLE_openjcockpit-admin")))
                        .contentType("application/json").content("{\"workspaceKey\":\"k\"}"))
               .andExpect(status().isForbidden());
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
