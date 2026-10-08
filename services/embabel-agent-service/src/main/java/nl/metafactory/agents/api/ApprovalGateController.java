package nl.metafactory.agents.api;

import nl.metafactory.agents.approval.ApprovalDecisionAuditRepository;
import nl.metafactory.agents.approval.ApprovalDecisionService;
import nl.metafactory.agents.approval.ApprovalGateContextAssembler;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.approval.model.ApprovalDecisionAuditEntry;
import nl.metafactory.agents.approval.model.ApprovalDecisionCommand;
import nl.metafactory.agents.approval.model.ApprovalDecisionRequest;
import nl.metafactory.agents.approval.model.ApprovalDecisionResult;
import nl.metafactory.agents.approval.model.ApprovalGateContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * The three approval-gate endpoints (Frozen Contract #1). Identity for the audit trail always
 * comes from the caller's JWT — never a request field — following the existing
 * {@code preferred_username}/{@code sub} pattern (V5, {@code AgenticWorkflowController.java:63-68}
 * in ai-control-service). A client-supplied actor would be trivially spoofable.
 */
@RestController
@RequestMapping("/api/agent-runs/{runId}")
public class ApprovalGateController {

    private final ApprovalGateRegistry registry;
    private final ApprovalGateContextAssembler contextAssembler;
    private final ApprovalDecisionService decisionService;
    private final ApprovalDecisionAuditRepository auditRepository;

    public ApprovalGateController(ApprovalGateRegistry registry, ApprovalGateContextAssembler contextAssembler,
                                   ApprovalDecisionService decisionService,
                                   ApprovalDecisionAuditRepository auditRepository) {
        this.registry = registry;
        this.contextAssembler = contextAssembler;
        this.decisionService = decisionService;
        this.auditRepository = auditRepository;
    }

    @GetMapping("/approval-gate")
    public ApprovalGateContext getApprovalGateContext(@PathVariable String runId) {
        var state = registry.get(runId);
        if (state == null || state.gate() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No approval gate open for run: " + runId);
        }
        return contextAssembler.assemble(state);
    }

    @PostMapping("/approval-gate/decision")
    public ApprovalDecisionResult submitApprovalDecision(@PathVariable String runId,
                                                          @RequestBody ApprovalDecisionRequest request) {
        var command = new ApprovalDecisionCommand(request.decision(), request.iteration(), request.comment(),
                currentUsername(), currentSubject());
        return decisionService.submit(runId, command);
    }

    @GetMapping("/approval-decisions")
    public List<ApprovalDecisionAuditEntry> listApprovalDecisions(@PathVariable String runId) {
        return auditRepository.findByRunId(runId);
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof Jwt jwt)
                ? jwt.getClaimAsString("preferred_username")
                : "unknown";
    }

    private String currentSubject() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof Jwt jwt)
                ? jwt.getClaimAsString("sub")
                : "unknown";
    }
}
