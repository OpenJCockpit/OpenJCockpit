package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.generated.api.AgenticWorkflowApi;
import nl.metafactory.aicontrol.model.GitWorkspaceJobDto;
import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;
import nl.metafactory.aicontrol.model.PreflightError;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueActiveItemGuard;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import nl.metafactory.aicontrol.model.GitWorkspaceJobEventDto;
import nl.metafactory.aicontrol.model.StartWorkflowRequest;
import nl.metafactory.aicontrol.model.StartWorkflowResponse;
import nl.metafactory.aicontrol.model.WorkflowPreflightResult;
import nl.metafactory.aicontrol.service.GitWorkspaceJobService;
import nl.metafactory.aicontrol.service.WorkflowPreflightService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class AgenticWorkflowController implements AgenticWorkflowApi {

    private final GitWorkspaceJobService jobService;
    private final WorkflowPreflightService preflightService;
    private final SpecQueueActiveItemGuard activeItemGuard;

    public AgenticWorkflowController(GitWorkspaceJobService jobService, WorkflowPreflightService preflightService,
                                     SpecQueueActiveItemGuard activeItemGuard) {
        this.jobService = jobService;
        this.preflightService = preflightService;
        this.activeItemGuard = activeItemGuard;
    }

    @Override
    public ResponseEntity<StartWorkflowResponse> startWorkflow(StartWorkflowRequest request) {
        UUID projectId = request.projectId();
        activeItemGuard.assertNoActiveItem(projectId);
        WorkflowPreflightResult preflight = preflightService.validateBeforeWorkflowStart(projectId);
        if (!preflight.passed()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(StartWorkflowResponse.failed(preflight.errors()));
        }

        String username = currentUsername();
        GitWorkspaceJobDto job = jobService.createJob(projectId, request.specFileRef(), username);
        jobService.runWorkflowAsync(job.id());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(StartWorkflowResponse.success(job.id()));
    }

    @Override
    public ResponseEntity<WorkflowPreflightResult> checkPreflight(UUID projectId) {
        return ResponseEntity.ok(preflightService.validateBeforeWorkflowStart(projectId));
    }

    @Override
    public ResponseEntity<GitWorkspaceJobDto> getJob(UUID jobId) {
        return ResponseEntity.ok(jobService.findById(jobId));
    }

    @Override
    public ResponseEntity<List<GitWorkspaceJobEventDto>> getJobEvents(UUID jobId) {
        return ResponseEntity.ok(jobService.findEvents(jobId));
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof Jwt jwt)
                ? jwt.getClaimAsString("preferred_username")
                : "unknown";
    }

    @ExceptionHandler(SpecQueueException.class)
    public ResponseEntity<StartWorkflowResponse> handleSpecQueueException(SpecQueueException e) {
        return ResponseEntity.status(e.getHttpStatus()).body(StartWorkflowResponse.failed(List.of(
                new PreflightError(GitWorkspaceJobErrorCode.SPEC_QUEUE_ITEM_ACTIVE, e.getMessage()))));
    }
}
