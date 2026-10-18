package nl.metafactory.agents.config;

import com.embabel.common.ai.model.ModelProvider;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import nl.metafactory.agents.approval.ApprovalDecisionAuditRepository;
import nl.metafactory.agents.approval.ApprovalDecisionService;
import nl.metafactory.agents.approval.ApprovalGateContextAssembler;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import nl.metafactory.agents.security.RunnerAwareJwtDecoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK, properties =
        "openjcockpit.security.spec-queue-runner.secret=test-only-spec-queue-runner-secret-32-bytes-minimum")
@AutoConfigureMockMvc
class SpecQueueRunnerSecurityIntegrationTest {

    private static final String SECRET = "test-only-spec-queue-runner-secret-32-bytes-minimum";
    private static final String ISSUER = "urn:openjcockpit:ai-control-service:spec-queue-runner";

    @MockitoBean ModelProvider modelProvider;
    @MockitoBean AgentOrchestrator orchestrator;
    @MockitoBean ApprovalGateRegistry approvalGateRegistry;
    @MockitoBean ApprovalGateContextAssembler approvalGateContextAssembler;
    @MockitoBean ApprovalDecisionService approvalDecisionService;
    @MockitoBean ApprovalDecisionAuditRepository approvalDecisionAuditRepository;
    @MockitoBean nl.metafactory.agents.workflowtrigger.WorkflowTriggerCoordinator workflowTriggerCoordinator;

    @Autowired MockMvc mockMvc;
    @Autowired JwtDecoder jwtDecoder;

    private static String token(String secret, String audience, long lifetimeSeconds) throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer(ISSUER).audience(List.of(audience))
                .subject("spec-queue-runner").claim("preferred_username", "spec-queue-runner")
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(lifetimeSeconds))).build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes()));
        return "Bearer " + jwt.serialize();
    }

    private static String runner() throws Exception {
        return token(SECRET, "embabel-agent-service", 60);
    }

    @Test
    void decoderIsRunnerAware() {
        assertThat(jwtDecoder).isInstanceOf(RunnerAwareJwtDecoder.class);
    }

    @Test
    void runnerReachesOnlyAllowListedRoutes() throws Exception {
        for (String path : List.of("/api/workflows/x", "/api/workflow-groups/g", "/api/agent-runs/r")) {
            int s = mockMvc.perform(get(path).header("Authorization", runner())).andReturn().getResponse().getStatus();
            assertThat(s).as(path).isNotIn(401, 403);
        }
        int start = mockMvc.perform(post("/api/workflows/x/start").header("Authorization", runner())
                .contentType("application/json").content("{}")).andReturn().getResponse().getStatus();
        assertThat(start).isNotIn(401, 403);
    }

    @Test
    void runnerIsForbiddenEverywhereElse() throws Exception {
        for (var req : List.of(get("/api/workflows/export"), get("/api/workflows"), get("/api/agents"),
                get("/api/workflow-groups"), post("/api/workflows"), delete("/api/workflows/x"),
                post("/api/agent-runs"), get("/api/workflows/x/executions"),
                get("/api/workflow-executions/x/decision-logs"), post("/api/workflow-groups"),
                get("/api/agent-runs/r/approval-gate"), get("/api/unknown-route"))) {
            mockMvc.perform(req.header("Authorization", runner())).andExpect(status().isForbidden());
        }
    }

    @Test
    void invalidRunnerTokensAre401() throws Exception {
        for (String bad : List.of(token("another-secret-that-is-also-long-enough-xx", "embabel-agent-service", 60),
                token(SECRET, "wrong-audience", 60), token(SECRET, "embabel-agent-service", 600))) {
            mockMvc.perform(get("/api/workflows/x").header("Authorization", bad)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/workflows/x")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRoutesStayProtected() throws Exception {
        mockMvc.perform(get("/api/definitely-not-here")).andExpect(status().isUnauthorized());
    }
}
