package nl.metafactory.agents.workflowtrigger;

import nl.metafactory.agents.orchestration.PipelineState;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

/** A parent PipelineState parked waiting on a SEQUENTIAL child workflow run to reach a terminal status. */
public record ChildWait(
        PipelineState parentState,
        String childRunId,
        String childWorkflowId,
        String childWorkflowName,
        String anchorStage,
        Instant parkedAt,
        ScheduledFuture<?> deadline
) {
}
