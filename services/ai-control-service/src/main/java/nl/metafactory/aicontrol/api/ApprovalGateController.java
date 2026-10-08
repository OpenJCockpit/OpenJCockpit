package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.client.ApprovalDecisionAuditEntryDto;
import nl.metafactory.aicontrol.client.ApprovalDecisionRequestDto;
import nl.metafactory.aicontrol.client.ApprovalDecisionResultDto;
import nl.metafactory.aicontrol.client.ApprovalGateContextDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.generated.api.ApprovalGateApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Thin proxy for the approval-gate endpoints, following the {@link AgentController} pattern:
 * no gate business logic lives here (no stage-name check, no {@code feedbackSupported}
 * derivation) — it only relays to {@code embabel-agent-service} via {@link EmbabelAgentClient},
 * including that service's AC-59 400 rejection and AC-31/AC-32 409 conflict.
 */
@RestController
public class ApprovalGateController implements ApprovalGateApi {

    private final EmbabelAgentClient embabelAgentClient;

    public ApprovalGateController(EmbabelAgentClient embabelAgentClient) {
        this.embabelAgentClient = embabelAgentClient;
    }

    @Override
    public ResponseEntity<ApprovalGateContextDto> getApprovalGateContext(String runId) {
        ApprovalGateContextDto context = embabelAgentClient.getApprovalGateContext(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No approval gate open for run: " + runId));
        return ResponseEntity.ok(context);
    }

    @Override
    public ResponseEntity<ApprovalDecisionResultDto> submitApprovalDecision(String runId,
                                                                             ApprovalDecisionRequestDto request) {
        return ResponseEntity.ok(embabelAgentClient.submitApprovalDecision(runId, request));
    }

    @Override
    public ResponseEntity<List<ApprovalDecisionAuditEntryDto>> listApprovalDecisions(String runId) {
        return ResponseEntity.ok(embabelAgentClient.listApprovalDecisions(runId));
    }
}
