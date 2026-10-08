package nl.metafactory.agents.workflow.model;

import java.time.Instant;

public record WorkflowStartResponse(
        String workflowId,
        String executionId,
        String status,
        Instant startedAt,
        String message
) {
}
