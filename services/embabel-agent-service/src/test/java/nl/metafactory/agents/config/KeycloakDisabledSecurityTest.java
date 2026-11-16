package nl.metafactory.agents.config;

import nl.metafactory.agents.approval.ApprovalDecisionAuditRepository;
import nl.metafactory.agents.approval.ApprovalDecisionService;
import nl.metafactory.agents.approval.ApprovalGateContextAssembler;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import nl.metafactory.agents.workflowtrigger.WorkflowTriggerCoordinator;
import nl.metafactory.common.agentruntime.AgentRuntimeApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK, properties = "ENABLE_KEYCLOAK=false")
@AutoConfigureMockMvc
class KeycloakDisabledSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired ApplicationContext context;
    @MockitoBean AgentOrchestrator orchestrator;
    @MockitoBean ApprovalGateRegistry approvalGateRegistry;
    @MockitoBean ApprovalGateContextAssembler approvalGateContextAssembler;
    @MockitoBean ApprovalDecisionService approvalDecisionService;
    @MockitoBean ApprovalDecisionAuditRepository approvalDecisionAuditRepository;
    @MockitoBean WorkflowTriggerCoordinator workflowTriggerCoordinator;

    @Test
    void withoutKeycloakEveryRequestIsPermittedAsTheLocalIdentity() throws Exception {
        assertThat(context.getBeanNamesForType(SecurityConfig.class)).isEmpty();
        assertThat(context.getBean(Jwt.class).getSubject()).isEqualTo("developer");

        mockMvc.perform(get("/api/agent-definitions")).andExpect(status().isOk());
        mockMvc.perform(get(AgentRuntimeApi.TENANT_BINDING, "local", "unknown-agent")).andExpect(status().isNotFound());
    }

    @Test
    void productionRefusesToRunWithoutKeycloak() {
        var production = new MockEnvironment();
        production.setActiveProfiles("prod");
        assertThatThrownBy(() -> new KeycloakDisabledSecurityConfig(production))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SecurityConfig.KEYCLOAK_ENABLED);

        var local = new MockEnvironment();
        local.setActiveProfiles("local");
        assertThatCode(() -> new KeycloakDisabledSecurityConfig(local)).doesNotThrowAnyException();
    }
}
