package nl.metafactory.aicontrol.specqueue.app;

import nl.metafactory.aicontrol.config.SpecQueueProperties;
import nl.metafactory.aicontrol.model.SpecQueueDto;
import nl.metafactory.aicontrol.model.SpecQueueItemDto;
import nl.metafactory.aicontrol.model.SpecQueueItemStatus;
import nl.metafactory.aicontrol.model.SpecQueueMergeState;
import nl.metafactory.aicontrol.model.SpecQueuePollErrorDto;
import nl.metafactory.aicontrol.model.SpecQueueRunnerStatusDto;
import nl.metafactory.aicontrol.model.SpecQueueSettingsDto;
import nl.metafactory.aicontrol.model.SpecQueueState;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueue;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Pure mapping, no I/O. */
@Component
public class SpecQueueDtoMapper {

    private final SpecQueueProperties properties;

    public SpecQueueDtoMapper(SpecQueueProperties properties) {
        this.properties = properties;
    }

    public SpecQueueItemDto toItemDto(SpecQueueItem item, Integer rankOrNull, List<String> prUrls) {
        SpecQueueMergeState mergeState = null;
        if (item.getStatus() == SpecQueueItemStatus.MERGING) {
            mergeState = item.getMergeAttemptStartedAt() == null
                    ? SpecQueueMergeState.WAITING_FOR_MERGEABILITY
                    : SpecQueueMergeState.MERGE_IN_PROGRESS;
        }
        return new SpecQueueItemDto(
                item.getId(),
                item.getProjectId(),
                item.getSpecFile(),
                item.getWorkflowId(),
                item.getWorkflowName(),
                item.isAutoMerge(),
                rankOrNull,
                item.getStatus(),
                item.getFailureReason(),
                item.getWorkflowRunId(),
                item.getLastRunStatus(),
                mergeState,
                List.copyOf(prUrls),
                item.getCreatedByUsername() != null ? item.getCreatedByUsername() : item.getCreatedBySub(),
                item.getCreatedAt(),
                item.getStartedAt(),
                item.getFinishedAt());
    }

    /** {@code queue} may be null (no row yet): the queue then reads as the transient default. */
    public SpecQueueDto toQueueDto(UUID projectId, SpecQueue queue, List<SpecQueueItemDto> items,
                                   List<SpecQueueItemDto> recentlyFinished) {
        SpecQueuePollErrorDto pollError = queue != null && queue.getLastPollErrorCode() != null
                ? new SpecQueuePollErrorDto(queue.getLastPollErrorCode(), queue.getLastPollErrorAt())
                : null;
        return new SpecQueueDto(
                projectId,
                queue != null ? queue.getState() : SpecQueueState.ACTIVE,
                queue != null && queue.isAutoMergeAllowed(),
                items,
                recentlyFinished,
                queue != null ? queue.getLastPolledAt() : null,
                pollError,
                new SpecQueueRunnerStatusDto(properties.getRunner().isEnabled(),
                        properties.getRunner().getToken().isConfigured()));
    }

    public SpecQueueSettingsDto toSettingsDto(SpecQueue queue) {
        return queue == null
                ? new SpecQueueSettingsDto(false, null)
                : new SpecQueueSettingsDto(queue.isAutoMergeAllowed(), queue.getSettingsUpdatedAt());
    }
}
