import { describe, expect, it } from 'vitest';
import {
  failureReasonText,
  isOpenItem,
  itemActions,
  itemPresentation,
  itemStatusPresentation,
  pollErrorText,
  queueStateText,
  safeHttpUrl,
} from './queueStatus';
import type { SpecQueueFailureReason, SpecQueueItemStatus } from './specQueueTypes';

const STATUSES: SpecQueueItemStatus[] = [
  'QUEUED',
  'STARTING',
  'RUNNING',
  'AWAITING_MERGE',
  'MERGING',
  'MERGED',
  'COMPLETED_NO_CHANGES',
  'FAILED',
  'SKIPPED',
  'CANCELLED',
  'REMOVED',
];

const REASONS: SpecQueueFailureReason[] = [
  'WORKFLOW_INVALID',
  'PREFLIGHT_FAILED',
  'START_REJECTED',
  'START_OUTCOME_UNKNOWN',
  'RUN_FAILED',
  'RUN_CANCELLED',
  'APPROVAL_DENIED',
  'RUN_BLOCKED',
  'RUN_TIMED_OUT',
  'RUN_STATE_LOST',
  'PR_NOT_CREATED',
  'PUBLICATION_FAILED',
  'PR_URL_INVALID',
  'PR_HOST_UNSUPPORTED',
  'PR_CLOSED_UNMERGED',
  'MERGE_CONFLICT',
  'MERGE_BLOCKED',
  'MERGE_AUTH_FAILED',
  'MERGE_OUTCOME_UNKNOWN',
];

describe('queueStatus', () => {
  it('gives every status a label and icon', () => {
    for (const status of STATUSES) {
      const p = itemStatusPresentation(status);
      expect(p.label).not.toBe('');
      expect(p.icon).not.toBe('');
    }
  });

  it('shows "Awaiting approval" only for RUNNING at an approval gate', () => {
    expect(itemPresentation({ status: 'RUNNING', runStatus: 'AWAITING_APPROVAL' }).label).toBe(
      'Awaiting approval',
    );
    expect(itemPresentation({ status: 'RUNNING', runStatus: 'RUNNING' }).label).toBe('Running');
    expect(itemPresentation({ status: 'QUEUED', runStatus: 'AWAITING_APPROVAL' }).label).toBe(
      'Queued',
    );
  });

  it('has 19 distinct failure texts', () => {
    const texts = REASONS.map(failureReasonText);
    expect(texts.every(Boolean)).toBe(true);
    expect(new Set(texts).size).toBe(19);
  });

  it('has poll-error and state texts', () => {
    expect(pollErrorText('GITHUB_RATE_LIMITED')).toBe('GitHub rate limit reached');
    expect(queueStateText('ACTIVE')).toBe('Active');
    expect(queueStateText('PAUSED')).toBe('Paused');
    expect(queueStateText('HALTED')).toBe('Halted');
  });

  it('derives the action matrix per status', () => {
    const none = {
      canMove: false,
      canEdit: false,
      canRemove: false,
      canCancel: false,
      canRetry: false,
      canSkip: false,
    };
    expect(itemActions('QUEUED')).toEqual({
      ...none,
      canMove: true,
      canEdit: true,
      canRemove: true,
    });
    expect(itemActions('RUNNING')).toEqual({ ...none, canCancel: true });
    expect(itemActions('AWAITING_MERGE')).toEqual({ ...none, canCancel: true });
    expect(itemActions('FAILED')).toEqual({ ...none, canRetry: true, canSkip: true });
    for (const s of ['STARTING', 'MERGING', 'MERGED', 'COMPLETED_NO_CHANGES', 'SKIPPED'] as const) {
      expect(itemActions(s)).toEqual(none);
    }
  });

  it('knows the open statuses', () => {
    const open = STATUSES.filter(isOpenItem);
    expect(open).toEqual(['QUEUED', 'STARTING', 'RUNNING', 'AWAITING_MERGE', 'MERGING']);
  });

  it('safeHttpUrl accepts http(s) and rejects everything else', () => {
    expect(safeHttpUrl('https://github.com/o/r/pull/1')).toBe('https://github.com/o/r/pull/1');
    expect(safeHttpUrl('http://example.com')).toBe('http://example.com');
    expect(safeHttpUrl('javascript:alert(1)')).toBeNull();
    expect(safeHttpUrl('data:text/html,hi')).toBeNull();
    expect(safeHttpUrl('not a url')).toBeNull();
  });
});
