package nl.metafactory.aicontrol.config;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.AgentRunDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired MockMvc mockMvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;
    @MockitoBean EmbabelAgentClient embabelAgentClient;
    @MockitoBean JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(embabelAgentClient.getLatestRun("run-1")).thenReturn(java.util.Optional.of(new AgentRunDto("run-1", "cust",
                "spec", "", "COMPLETED", java.time.Instant.now(), List.of(), List.of(), null, null, null, null)));
    }

    /** A Keycloak-shaped token decoded by the (mocked) decoder and converted by the real converter. */
    private String bearer(String tokenValue, String azp, String... realmRoles) {
        Jwt jwt = Jwt.withTokenValue(tokenValue).header("alg", "none").subject("sub-" + tokenValue)
                .claim("preferred_username", tokenValue).claim("azp", azp)
                .claim("realm_access", Map.of("roles", List.of(realmRoles)))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(jwtDecoder.decode(tokenValue)).thenReturn(jwt);
        return "Bearer " + tokenValue;
    }

    @Test
    void unauthenticatedRequestIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/agent-runs/run-1"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestWithoutAnyRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/api/agent-runs/run-1").header("Authorization", bearer("nobody", "openjcockpit")))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void viewerRealmRoleFromTheTokenAllowsReading() throws Exception {
        mockMvc.perform(get("/api/agent-runs/run-1")
                        .header("Authorization", bearer("viewer", "openjcockpit", "openjcockpit-viewer")))
               .andExpect(status().isOk());
    }

    @Test
    void adminImpliesEveryRoleThroughTheRoleHierarchy() throws Exception {
        String admin = bearer("admin", "openjcockpit", "openjcockpit-admin");
        mockMvc.perform(get("/api/agent-runs/run-1").header("Authorization", admin)).andExpect(status().isOk());
        when(embabelAgentClient.getApprovalGateContext("run-1")).thenReturn(java.util.Optional.empty());
        mockMvc.perform(get("/api/agent-runs/run-1/approval-gate").header("Authorization", admin))
               .andExpect(status().isNotFound());
    }

    @Test
    void operatorCannotDecideAnApprovalGateAndNothingIsRelayed() throws Exception {
        mockMvc.perform(post("/api/agent-runs/run-1/approval-gate/decision")
                        .header("Authorization", bearer("operator", "openjcockpit", "openjcockpit-operator"))
                        .contentType(APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}"))
               .andExpect(status().isForbidden());

        verify(embabelAgentClient, never()).submitApprovalDecision(anyString(), any());
    }

    @Test
    void approverCannotStartAWorkflowAndNothingIsRelayed() throws Exception {
        mockMvc.perform(post("/api/workflows/wf-1/start")
                        .header("Authorization", bearer("approver", "openjcockpit", "openjcockpit-approver"))
                        .contentType(APPLICATION_JSON).content("{}"))
               .andExpect(status().isForbidden());

        verifyNoInteractions(embabelAgentClient);
    }

    @Test
    void nonAdminCannotChangeSpecQueueSettingsEvenWithOperatorAndApprover() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/projects/11111111-0000-4000-a000-000000000001/spec-queue/settings")
                        .header("Authorization", bearer("koen", "openjcockpit", "openjcockpit-operator",
                                "openjcockpit-approver"))
                        .contentType(APPLICATION_JSON).content("{\"autoMergeAllowed\":true}"))
               .andExpect(status().isForbidden());
    }

    @Test
    void internalGitWorkspaceCredentialsRejectAHumanTokenEvenForAnAdmin() throws Exception {
        mockMvc.perform(get("/internal/git-workspaces/ws-1/credentials").param("repositoryUrl", "https://x/y.git")
                        .header("Authorization", bearer("tony", "openjcockpit", "openjcockpit-admin")))
               .andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/git-workspaces/ws-1/credentials").param("repositoryUrl", "https://x/y.git"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void internalGitWorkspaceCredentialsAdmitTheConfiguredServiceClientWithoutRealmRoles() throws Exception {
        mockMvc.perform(get("/internal/git-workspaces/ws-unknown/credentials").param("repositoryUrl", "https://x/y.git")
                        .header("Authorization", bearer("git-mcp", "openjcockpit-git-mcp")))
               .andExpect(status().isNotFound());
    }

    /**
     * Fail-closed guard: every handler mapping the application serves must appear in the authorization
     * matrix (so it has a deliberate role), and the matrix must not list routes that no longer exist.
     */
    @Test
    void everyHandlerMappingIsInTheAuthorizationMatrix() {
        Set<String> served = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            for (String pattern : info.getPatternValues()) {
                if (pattern.equals("/error")) {
                    continue;
                }
                assertThat(info.getMethodsCondition().getMethods()).as(pattern).isNotEmpty();
                info.getMethodsCondition().getMethods().forEach(m -> served.add(m.name() + " " + normalise(pattern)));
            }
        }
        Set<String> matrix = new TreeSet<>();
        AuthorizationMatrixTest.ENDPOINTS.forEach(e -> matrix.add(e.method().name() + " " + normalise(e.template())));

        assertThat(served).isEqualTo(matrix);
    }

    private static String normalise(String template) {
        return template.replaceAll("\\{[^}]+}", "{}");
    }

    @Test
    void actuatorHealthIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
               .andExpect(status().isOk());
    }

    @Test
    void actuatorMetricsNeedAdmin() throws Exception {
        mockMvc.perform(get("/actuator/metrics").header("Authorization", bearer("ricky", "openjcockpit",
                        "openjcockpit-operator")))
               .andExpect(status().isForbidden());
        // The test profile may not expose metrics (404); what matters is that admin is not refused.
        int adminStatus = mockMvc.perform(get("/actuator/metrics").header("Authorization", bearer("tony",
                        "openjcockpit", "openjcockpit-admin"))).andReturn().getResponse().getStatus();
        assertThat(adminStatus).isNotIn(401, 403);
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
    void approvalGateContextWithViewerJwtIsAllowed() throws Exception {
        when(embabelAgentClient.getApprovalGateContext("run-1")).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/agent-runs/run-1/approval-gate")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_openjcockpit-viewer"))))
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
