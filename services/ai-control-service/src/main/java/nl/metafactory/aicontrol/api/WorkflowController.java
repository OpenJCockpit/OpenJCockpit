package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.PolicyDecisionAuditEntryDto;
import nl.metafactory.aicontrol.client.WorkflowDefinitionDto;
import nl.metafactory.aicontrol.client.WorkflowExportBundleDto;
import nl.metafactory.aicontrol.client.WorkflowImportResultDto;
import nl.metafactory.aicontrol.client.WorkflowStartInputDto;
import nl.metafactory.aicontrol.client.WorkflowStartResponseDto;
import nl.metafactory.aicontrol.generated.api.WorkflowApi;
import nl.metafactory.aicontrol.model.ApiErrorResponse;
import nl.metafactory.aicontrol.service.WorkflowStartEnrichmentService;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueActiveItemGuard;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
public class WorkflowController implements WorkflowApi {

    private final EmbabelAgentClient client;
    private final WorkflowStartEnrichmentService startEnrichment;
    // Lazy so @WebMvcTest slices without component scan still construct this controller.
    private final ObjectProvider<SpecQueueActiveItemGuard> activeItemGuard;

    public WorkflowController(EmbabelAgentClient client, WorkflowStartEnrichmentService startEnrichment,
                              ObjectProvider<SpecQueueActiveItemGuard> activeItemGuard) {
        this.client = client;
        this.startEnrichment = startEnrichment;
        this.activeItemGuard = activeItemGuard;
    }

    // NOTE: operationId "list" is a reserved word for the spring generator template
    // and is auto-renamed to "callList" in the generated WorkflowApi interface.
    @Override
    public ResponseEntity<List<WorkflowDefinitionDto>> callList() {
        return ResponseEntity.ok(client.listWorkflows());
    }

    @Override
    public ResponseEntity<WorkflowExportBundleDto> export() {
        return ResponseEntity.ok(client.exportWorkflows());
    }

    @Override
    public ResponseEntity<WorkflowImportResultDto> importBundle(WorkflowExportBundleDto bundle) {
        return ResponseEntity.ok(client.importWorkflows(bundle));
    }

    @Override
    public ResponseEntity<WorkflowDefinitionDto> get(String id) {
        WorkflowDefinitionDto workflow = client.getWorkflow(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found: " + id));
        return ResponseEntity.ok(workflow);
    }

    @Override
    public ResponseEntity<WorkflowDefinitionDto> create(WorkflowDefinitionDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(client.createWorkflow(request));
    }

    @Override
    public ResponseEntity<WorkflowDefinitionDto> update(String id, WorkflowDefinitionDto request) {
        return ResponseEntity.ok(client.updateWorkflow(id, request));
    }

    @Override
    public ResponseEntity<Void> delete(String id) {
        client.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<WorkflowStartResponseDto> start(String id, WorkflowStartInputDto input) {
        WorkflowStartInputDto requestedInput = input;
        if (requestedInput == null) {
            requestedInput = WorkflowStartInputDto.empty();
        }
        WorkflowStartInputDto safe = WorkflowStartInputs.validateCallerBaseBranch(requestedInput);
        UUID projectId = parseProjectId(safe.projectId());
        activeItemGuard.ifAvailable(guard -> guard.assertNoActiveItem(projectId));
        return ResponseEntity.ok(client.startWorkflow(id, startEnrichment.enrich(safe)));
    }

    @Override
    public ResponseEntity<List<PolicyDecisionAuditEntryDto>> decisionLogs(
            String id, Instant from, Instant to, String agentId, String subagentId,
            String skillId, String mcpToolName, String result) {
        return ResponseEntity.ok(
                client.getDecisionLogsForWorkflow(id, from, to, agentId, subagentId, skillId, mcpToolName, result));
    }

    // Lenient on purpose: a missing or malformed projectId means "not guarded", behaviour unchanged.
    private static UUID parseProjectId(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(projectId.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @ExceptionHandler(SpecQueueException.class)
    public ResponseEntity<ApiErrorResponse> handleSpecQueueException(SpecQueueException e) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(new ApiErrorResponse(e.getCode().name(), e.getMessage()));
    }
}
