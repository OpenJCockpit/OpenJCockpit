import type { StatusKind } from '../types';
import type {
  SpecQueueFailureReason,
  SpecQueueItem,
  SpecQueueItemStatus,
  SpecQueuePollErrorCode,
  SpecQueueState,
} from './specQueueTypes';

export interface StatusPresentation {
  label: string;
  icon: string;
  kind: StatusKind;
}

const STATUS: Record<SpecQueueItemStatus, StatusPresentation> = {
  QUEUED: { label: 'Queued', icon: '⏳', kind: 'waiting' },
  STARTING: { label: 'Starting', icon: '⟳', kind: 'active' },
  RUNNING: { label: 'Running', icon: '▶', kind: 'active' },
  AWAITING_MERGE: { label: 'Awaiting merge', icon: '⏸', kind: 'review' },
  MERGING: { label: 'Merging', icon: '⇄', kind: 'active' },
  MERGED: { label: 'Merged', icon: '✔', kind: 'ok' },
  COMPLETED_NO_CHANGES: { label: 'Completed, no changes', icon: '✔', kind: 'ok' },
  FAILED: { label: 'Failed', icon: '✕', kind: 'changes' },
  SKIPPED: { label: 'Skipped', icon: '⤼', kind: 'done' },
  CANCELLED: { label: 'Cancelled', icon: '⊘', kind: 'done' },
  REMOVED: { label: 'Removed', icon: '⊘', kind: 'done' },
};

export function itemStatusPresentation(status: SpecQueueItemStatus): StatusPresentation {
  return STATUS[status];
}

export function itemPresentation(
  item: Pick<SpecQueueItem, 'status' | 'runStatus'>,
): StatusPresentation {
  if (item.status === 'RUNNING' && item.runStatus === 'AWAITING_APPROVAL') {
    return { label: 'Awaiting approval', icon: '⏸', kind: 'review' };
  }
  return itemStatusPresentation(item.status);
}

const FAILURE_TEXT: Record<SpecQueueFailureReason, string> = {
  WORKFLOW_INVALID: 'The workflow is no longer valid for this project',
  PREFLIGHT_FAILED: 'Pre-flight checks failed',
  START_REJECTED: 'The workflow start was rejected',
  START_OUTCOME_UNKNOWN: 'It is unknown whether the run started — check the workflow executions',
  RUN_FAILED: 'The run failed',
  RUN_CANCELLED: 'The run was cancelled outside the queue',
  APPROVAL_DENIED: 'The approval was denied',
  RUN_BLOCKED: 'The run was blocked',
  RUN_TIMED_OUT: 'The run timed out',
  RUN_STATE_LOST: 'The run state was lost',
  PR_NOT_CREATED: 'The run produced no pull request',
  PUBLICATION_FAILED: 'Publishing the changes failed',
  PR_URL_INVALID: 'The pull request URL is invalid',
  PR_HOST_UNSUPPORTED: 'The pull request host is not supported',
  PR_CLOSED_UNMERGED: 'The pull request was closed without merging',
  MERGE_CONFLICT: 'The pull request has a merge conflict',
  MERGE_BLOCKED: 'The merge is blocked by checks or reviews',
  MERGE_AUTH_FAILED: 'Not authorised to merge the pull request',
  MERGE_OUTCOME_UNKNOWN: 'It is unknown whether the merge happened — check the pull request',
};

export function failureReasonText(reason: SpecQueueFailureReason): string {
  return FAILURE_TEXT[reason];
}

const POLL_ERROR_TEXT: Record<SpecQueuePollErrorCode, string> = {
  EMBABEL_UNAVAILABLE: 'The agent service is unavailable',
  EMBABEL_UNAUTHORIZED: 'The queue runner is not authorised by the agent service',
  RUNNER_NOT_CONFIGURED: 'The queue runner has no identity configured',
  GITHUB_UNAVAILABLE: 'GitHub is unavailable',
  GITHUB_RATE_LIMITED: 'GitHub rate limit reached',
};

export function pollErrorText(code: SpecQueuePollErrorCode): string {
  return POLL_ERROR_TEXT[code];
}

const STATE_TEXT: Record<SpecQueueState, string> = {
  ACTIVE: 'Active',
  PAUSED: 'Paused',
  HALTED: 'Halted',
};

export function queueStateText(state: SpecQueueState): string {
  return STATE_TEXT[state];
}

export interface ItemActions {
  canMove: boolean;
  canEdit: boolean;
  canRemove: boolean;
  canCancel: boolean;
  canRetry: boolean;
  canSkip: boolean;
}

/** A hint for which controls to show; the server decides what is actually allowed. */
export function itemActions(status: SpecQueueItemStatus): ItemActions {
  const queued = status === 'QUEUED';
  return {
    canMove: queued,
    canEdit: queued,
    canRemove: queued,
    canCancel: status === 'RUNNING' || status === 'AWAITING_MERGE',
    canRetry: status === 'FAILED',
    canSkip: status === 'FAILED',
  };
}

const OPEN: ReadonlySet<SpecQueueItemStatus> = new Set([
  'QUEUED',
  'STARTING',
  'RUNNING',
  'AWAITING_MERGE',
  'MERGING',
]);

export function isOpenItem(status: SpecQueueItemStatus): boolean {
  return OPEN.has(status);
}

export function safeHttpUrl(url: string): string | null {
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? url : null;
  } catch {
    return null;
  }
}
