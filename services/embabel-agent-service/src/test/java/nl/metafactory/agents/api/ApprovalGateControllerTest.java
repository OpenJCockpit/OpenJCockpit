package nl.metafactory.agents.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.agents.approval.ApprovalDecisionAuditRepository;
import nl.metafactory.agents.approval.ApprovalDecisionService;
import nl.metafactory.agents.approval.ApprovalGateContextAssembler;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.approval.model.ApprovalDecisionAuditEntry;
import nl.metafactory.agents.approval.model.ApprovalDecisionCommand;
import nl.metafactory.agents.approval.model.ApprovalDecisionKind;
import nl.metafactory.agents.approval.model.ApprovalDecisionRequest;
import nl.metafactory.agents.approval.model.ApprovalDecisionResult;
import nl.metafactory.agents.approval.model.ApprovalGateContext;
import nl.metafactory.agents.approval.model.StageOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    @MockitoBean ApprovalGateRegistry registry;
    @MockitoBean ApprovalGateContextAssembler contextAssembler;
    @MockitoBean ApprovalDecisionService decisionService;
    @MockitoBean ApprovalDecisionAuditRepository auditRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getApprovalGateContextReturns404WhenNothingIsParked() throws Exception {
        when(registry.get("run-1")).thenReturn(null);

        mockMvc.perform(get("/api/agent-runs/run-1/approval-gate"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getApprovalGateContextReturnsTheAssembledContextWhenParked() throws Exception {
        var state = mock(nl.metafactory.agents.orchestration.PipelineState.class);
        var gate = mock(nl.metafactory.agents.approval.model.ApprovalGateState.class);
        when(state.gate()).thenReturn(gate);
        when(registry.get("run-1")).thenReturn(state);
        var context = new ApprovalGateContext("run-1", "wf-1", "realisation", StageOutcome.PUBLISHED,
                null, "summary", List.of("a.txt"), 1, 0, "feat/wf-1-run",
                "https://github.com/org/repo/pull/9", null, 1, 3, true, true, List.of(), Instant.now());
        when(contextAssembler.assemble(state)).thenReturn(context);

        mockMvc.perform(get("/api/agent-runs/run-1/approval-gate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("run-1"))
                .andExpect(jsonPath("$.feedbackSupported").value(true));
    }

    @Test
    void submitApprovalDecisionReturns200OnSuccess() throws Exception {
        when(decisionService.submit(eq("run-1"), any()))
                .thenReturn(new ApprovalDecisionResult("run-1", "RUNNING", 1, "Accepted"));
        var request = new ApprovalDecisionRequest(ApprovalDecisionKind.ACCEPT, 1, null);

        mockMvc.perform(post("/api/agent-runs/run-1/approval-gate/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void submitApprovalDecisionRelays400() throws Exception {
        when(decisionService.submit(eq("run-1"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Accept with comments is not available"));
        var request = new ApprovalDecisionRequest(ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 1, "x");

        mockMvc.perform(post("/api/agent-runs/run-1/approval-gate/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitApprovalDecisionRelays404() throws Exception {
        when(decisionService.submit(eq("missing"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found: missing"));
        var request = new ApprovalDecisionRequest(ApprovalDecisionKind.ACCEPT, 1, null);

        mockMvc.perform(post("/api/agent-runs/missing/approval-gate/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void submitApprovalDecisionRelays409() throws Exception {
        when(decisionService.submit(eq("run-1"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "already decided"));
        var request = new ApprovalDecisionRequest(ApprovalDecisionKind.ACCEPT, 1, null);

        mockMvc.perform(post("/api/agent-runs/run-1/approval-gate/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void listApprovalDecisionsReturnsAuditTrail() throws Exception {
        var entry = new ApprovalDecisionAuditEntry("d-1", "run-1", "wf-1", "realisation", 1,
                ApprovalDecisionKind.ACCEPT, null, "alice", "sub-1", StageOutcome.PUBLISHED,
                "feat/wf-1-run", "https://github.com/org/repo/pull/9", Instant.now());
        when(auditRepository.findByRunId("run-1")).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/agent-runs/run-1/approval-decisions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("d-1"))
                .andExpect(jsonPath("$[0].actorUsername").value("alice"));
    }

    // ── AC-39: identity comes from the JWT, never from the request body ────────

    @Test
    void submitApprovalDecisionTakesActorIdentityFromTheJwtNotTheRequestBody() throws Exception {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("preferred_username", "alice")
                .claim("sub", "sub-alice")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        when(decisionService.submit(eq("run-1"), any()))
                .thenReturn(new ApprovalDecisionResult("run-1", "DENIED", 1, "Denied"));
        var request = new ApprovalDecisionRequest(ApprovalDecisionKind.DENY, 1, null);

        mockMvc.perform(post("/api/agent-runs/run-1/approval-gate/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(ApprovalDecisionCommand.class);
        verify(decisionService).submit(eq("run-1"), captor.capture());
        assertThat(captor.getValue().actorUsername()).isEqualTo("alice");
        assertThat(captor.getValue().actorSubject()).isEqualTo("sub-alice");
    }

    @Test
    void submitApprovalDecisionFallsBackToUnknownWhenNoAuthenticationIsPresent() throws Exception {
        when(decisionService.submit(eq("run-1"), any()))
                .thenReturn(new ApprovalDecisionResult("run-1", "DENIED", 1, "Denied"));
        var request = new ApprovalDecisionRequest(ApprovalDecisionKind.DENY, 1, null);

        mockMvc.perform(post("/api/agent-runs/run-1/approval-gate/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(ApprovalDecisionCommand.class);
        verify(decisionService).submit(eq("run-1"), captor.capture());
        assertThat(captor.getValue().actorUsername()).isEqualTo("unknown");
        assertThat(captor.getValue().actorSubject()).isEqualTo("unknown");
    }
}
