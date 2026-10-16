package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.generated.api.AgenticWorkflowApi;
import nl.metafactory.aicontrol.model.GitWorkspaceJobDto;
import nl.metafactory.aicontrol.model.GitWorkspaceJobEventDto;
import nl.metafactory.aicontrol.model.StartWorkflowRequest;
import nl.metafactory.aicontrol.model.StartWorkflowResponse;
import nl.metafactory.aicontrol.model.WorkflowPreflightResult;
import nl.metafactory.aicontrol.service.GitWorkspaceJobService;
import nl.metafactory.aicontrol.service.JobWorkflowCheck;
import nl.metafactory.aicontrol.service.WorkflowPreflightService;
import nl.metafactory.common.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class AgenticWorkflowController implements AgenticWorkflowApi {

    private final GitWorkspaceJobService jobService;
    private final WorkflowPreflightService preflightService;
    private final JobWorkflowCheck workflowCheck;

    public AgenticWorkflowController(GitWorkspaceJobService jobService, WorkflowPreflightService preflightService,
                                     JobWorkflowCheck workflowCheck) {
        this.jobService = jobService;
        this.preflightService = preflightService;
        this.workflowCheck = workflowCheck;
    }

    @Override
    public ResponseEntity<StartWorkflowResponse> startWorkflow(StartWorkflowRequest request) {
        UUID projectId = request.projectId();
        WorkflowPreflightResult preflight = preflightService.validateBeforeWorkflowStart(projectId);
        if (!preflight.passed()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(StartWorkflowResponse.failed(preflight.errors()));
        }

        var workflowProblem = workflowCheck.problem(request.workflowId());
        if (workflowProblem.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(StartWorkflowResponse.failed(List.of(workflowProblem.get())));
        }

        String username = CurrentUser.username();
        GitWorkspaceJobDto job = jobService.createJob(projectId, request.specFileRef(), request.workflowId(), username);
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
}
