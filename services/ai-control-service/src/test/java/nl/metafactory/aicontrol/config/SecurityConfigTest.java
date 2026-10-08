package nl.metafactory.aicontrol.config;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.AgentDefinitionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean EmbabelAgentClient embabelAgentClient;
    @MockitoBean JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(embabelAgentClient.getAgentDefinitions()).thenReturn(List.of(
                new AgentDefinitionDto("req", "Requirement Agent", "Extract requirements",
                        "specification", 0, "SpecContent", "RequirementAnalysis")
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

    // ── AC-14 / AC-15 (skills-marketplace-settings) ─────────────────────────────

    @Test
    void skillsMarketplacesWithoutAuthorizationHeaderIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/skills-marketplaces"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void skillsMarketplacesWithWrongIssuerOrExpiredTokenIsRejectedWith401() throws Exception {
        when(jwtDecoder.decode("wrong-issuer-token")).thenThrow(new BadJwtException("Invalid issuer"));

        mockMvc.perform(get("/api/skills-marketplaces").header("Authorization", "Bearer wrong-issuer-token"))
               .andExpect(status().isUnauthorized());
    }

    // ── AC-37 (workflow-approval-gate) ──────────────────────────────────────────

    @Test
    void approvalGateContextWithoutAuthorizationHeaderIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/agent-runs/run-1/approval-gate"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void approvalGateContextWithJwtIsAllowed() throws Exception {
        when(embabelAgentClient.getApprovalGateContext("run-1")).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/agent-runs/run-1/approval-gate").with(jwt()))
               .andExpect(status().isNotFound());
    }

    @Test
    void wellKnownOauthProtectedResourceMetadataIsNotAnonymous() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource"))
               .andExpect(status().isUnauthorized());
    }

    // ── MADP-54 AC-09 : starting a workflow (with a baseBranch in the body) requires a token ──

    @Test
    void startWorkflowWithoutBearerTokenIsRejectedWith401AndMakesNoDownstreamCall() throws Exception {
        mockMvc.perform(post("/api/workflows/wf-1/start")
                        .contentType(APPLICATION_JSON)
                        .content("{\"projectId\":\"11111111-2222-3333-4444-555555555555\",\"baseBranch\":\"develop\"}"))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(embabelAgentClient);
    }
}
