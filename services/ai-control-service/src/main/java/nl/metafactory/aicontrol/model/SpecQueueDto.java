package nl.metafactory.aicontrol.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SpecQueueDto(
        UUID projectId,
        SpecQueueState state,
        boolean autoMergeAllowed,
        List<SpecQueueItemDto> items,
        List<SpecQueueItemDto> recentlyFinished,
        Instant lastPolledAt,
        SpecQueuePollErrorDto lastPollError,
        SpecQueueRunnerStatusDto runner
) {}
