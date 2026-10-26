export type {
  SpecQueueState,
  SpecQueueItemStatus,
  SpecQueueMergeState,
  SpecQueueFailureReason,
  SpecQueuePollErrorDto as SpecQueuePollError,
  SpecQueueRunnerStatusDto as SpecQueueRunnerStatus,
  SpecQueueItemDto as SpecQueueItem,
  SpecQueueDto as SpecQueue,
  SpecQueueEnqueueRequest,
  SpecQueueItemUpdateRequest,
  SpecQueueSettingsDto as SpecQueueSettings,
} from '../generated/contract';
import type { SpecQueuePollErrorDto } from '../generated/contract';

export type SpecQueuePollErrorCode = SpecQueuePollErrorDto['code'];
