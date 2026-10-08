package nl.metafactory.aicontrol.api;

import jakarta.validation.ConstraintViolationException;
import nl.metafactory.aicontrol.client.AgentRunDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.PolicyDecisionAuditEntryDto;
import nl.metafactory.aicontrol.client.WorkflowExecutionPageDto;
import nl.metafactory.aicontrol.generated.api.WorkflowExecutionApi;
import nl.metafactory.aicontrol.model.ApiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
public class WorkflowExecutionController implements WorkflowExecutionApi {

    private final EmbabelAgentClient client;

    public WorkflowExecutionController(EmbabelAgentClient client) {
        this.client = client;
    }

    @Override
    public ResponseEntity<List<PolicyDecisionAuditEntryDto>> decisionLogs(
            String id, Instant from, Instant to, String agentId, String subagentId,
            String skillId, String mcpToolName, String result) {
        return ResponseEntity.ok(
                client.getDecisionLogsForWorkflowExecution(id, from, to, agentId, subagentId, skillId, mcpToolName, result));
    }

    @Override
    public ResponseEntity<WorkflowExecutionPageDto> listWorkflowExecutions(String id, Integer limit, Integer offset) {
        return ResponseEntity.ok(client.listWorkflowExecutionsOrThrow(id, limit, offset));
    }

    @Override
    public ResponseEntity<AgentRunDto> getWorkflowExecution(String id, String runId) {
        return ResponseEntity.ok(client.getWorkflowExecutionOrThrow(id, runId));
    }

    // F-7 fix: WorkflowExecutionApi is @Validated at the interface level, so out-of-range
    // limit/offset (@Min/@Max on the generated interface) are rejected by Spring's AOP method
    // validation proxy as a plain jakarta.validation.ConstraintViolationException, not the newer
    // HandlerMethodValidationException — left unhandled this surfaces as an unmapped 500. Per
    // ADR-3 (see SkillsMarketplaceController), this codebase uses controller-local
    // @ExceptionHandler methods rather than a global @ControllerAdvice.
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(WorkflowExecutionController::describeViolation)
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(new ApiErrorResponse("VALIDATION_ERROR", message));
    }

    private static String describeViolation(jakarta.validation.ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        String field = lastDot >= 0 ? path.substring(lastDot + 1) : path;
        return field + ": " + violation.getMessage();
    }
}
