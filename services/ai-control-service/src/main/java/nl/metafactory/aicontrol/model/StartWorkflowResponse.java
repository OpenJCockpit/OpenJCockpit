package nl.metafactory.aicontrol.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StartWorkflowResponse(
        String status,
        boolean preflightPassed,
        UUID jobId,
        String message,
        List<PreflightError> errors
) {

    public static StartWorkflowResponse success(UUID jobId) {
        return new StartWorkflowResponse("OK", true, jobId, "Workflow started.", null);
    }

    public static StartWorkflowResponse failed(List<PreflightError> errors) {
        return new StartWorkflowResponse("FAILED", false, null, null, errors);
    }
}
