package nl.metafactory.aicontrol.client;

import java.time.Instant;

public record WorkflowStartResponseDto(
        String workflowId,
        String executionId,
        String status,
        Instant startedAt,
        String message
) {
}
