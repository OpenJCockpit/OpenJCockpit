export type SpecQueueState = 'ACTIVE' | 'PAUSED' | 'HALTED';
export type SpecQueueItemStatus =
  | 'QUEUED'
  | 'STARTING'
  | 'RUNNING'
  | 'AWAITING_MERGE'
  | 'MERGING'
  | 'MERGED'
  | 'COMPLETED_NO_CHANGES'
  | 'FAILED'
  | 'SKIPPED'
  | 'CANCELLED'
  | 'REMOVED';
export type SpecQueueMergeState = 'WAITING_FOR_MERGEABILITY' | 'MERGE_IN_PROGRESS';
export type SpecQueueFailureReason =
  | 'WORKFLOW_INVALID'
  | 'PREFLIGHT_FAILED'
  | 'START_REJECTED'
  | 'START_OUTCOME_UNKNOWN'
  | 'RUN_FAILED'
  | 'RUN_CANCELLED'
  | 'APPROVAL_DENIED'
  | 'RUN_BLOCKED'
  | 'RUN_TIMED_OUT'
  | 'RUN_STATE_LOST'
  | 'PR_NOT_CREATED'
  | 'PUBLICATION_FAILED'
  | 'PR_URL_INVALID'
  | 'PR_HOST_UNSUPPORTED'
  | 'PR_CLOSED_UNMERGED'
  | 'MERGE_CONFLICT'
  | 'MERGE_BLOCKED'
  | 'MERGE_WAIT_TIMEOUT'
  | 'MERGE_AUTH_FAILED'
  | 'MERGE_OUTCOME_UNKNOWN';
export type SpecQueuePollErrorCode =
  | 'EMBABEL_UNAVAILABLE'
  | 'EMBABEL_UNAUTHORIZED'
  | 'RUNNER_NOT_CONFIGURED'
  | 'GITHUB_UNAVAILABLE'
  | 'GITHUB_RATE_LIMITED';

export interface SpecQueuePollError {
  code: SpecQueuePollErrorCode;
  at: string;
}
export interface SpecQueueRunnerStatus {
  enabled: boolean;
  configured: boolean;
}
export interface SpecQueueItem {
  id: string;
  projectId: string;
  specFile: string;
  workflowId: string;
  workflowName?: string | null;
  autoMerge: boolean;
  position?: number | null;
  status: SpecQueueItemStatus;
  failureReason?: SpecQueueFailureReason | null;
  workflowRunId?: string | null;
  runStatus?: string | null;
  mergeState?: SpecQueueMergeState | null;
  pullRequestUrls: string[];
  createdBy: string;
  createdAt: string;
  startedAt?: string | null;
  finishedAt?: string | null;
}
export interface SpecQueue {
  projectId: string;
  state: SpecQueueState;
  autoMergeAllowed: boolean;
  items: SpecQueueItem[];
  recentlyFinished: SpecQueueItem[];
  lastPolledAt?: string | null;
  lastPollError?: SpecQueuePollError | null;
  runner: SpecQueueRunnerStatus;
}
export interface SpecQueueEnqueueRequest {
  specFile: string;
  workflowId: string;
  autoMerge?: boolean;
}
export interface SpecQueueItemUpdateRequest {
  workflowId?: string;
  autoMerge?: boolean;
}
export interface SpecQueueSettings {
  autoMergeAllowed: boolean;
  updatedAt?: string | null;
}
