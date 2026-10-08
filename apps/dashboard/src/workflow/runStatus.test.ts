import { describe, expect, it } from 'vitest';

import { formatRunDuration, isTerminalRunStatus, runStatusKind, runStatusLabel } from './runStatus';

describe('runStatusKind', () => {
  it('returns ok for COMPLETED', () => {
    expect(runStatusKind('COMPLETED')).toBe('ok');
  });

  it('returns changes for FAILED', () => {
    expect(runStatusKind('FAILED')).toBe('changes');
  });

  it('returns changes for CANCELLED', () => {
    expect(runStatusKind('CANCELLED')).toBe('changes');
  });

  it('returns changes for DENIED', () => {
    expect(runStatusKind('DENIED')).toBe('changes');
  });

  it('returns changes for RUN_STATE_LOST', () => {
    expect(runStatusKind('RUN_STATE_LOST')).toBe('changes');
  });

  it('returns changes for NOT_FOUND', () => {
    expect(runStatusKind('NOT_FOUND')).toBe('changes');
  });

  it('returns waiting for AWAITING_APPROVAL', () => {
    expect(runStatusKind('AWAITING_APPROVAL')).toBe('waiting');
  });

  it('returns active for RUNNING', () => {
    expect(runStatusKind('RUNNING')).toBe('active');
  });

  it('returns waiting (safe fallback) for an unrecognized status without throwing', () => {
    expect(() => runStatusKind('SOMETHING_UNKNOWN')).not.toThrow();
    expect(runStatusKind('SOMETHING_UNKNOWN')).toBe('waiting');
  });
});

describe('runStatusLabel', () => {
  it('returns Awaiting approval for AWAITING_APPROVAL', () => {
    expect(runStatusLabel('AWAITING_APPROVAL')).toBe('Awaiting approval');
  });

  it('returns the raw status string unchanged for every other status', () => {
    expect(runStatusLabel('COMPLETED')).toBe('COMPLETED');
    expect(runStatusLabel('FAILED')).toBe('FAILED');
    expect(runStatusLabel('CANCELLED')).toBe('CANCELLED');
    expect(runStatusLabel('DENIED')).toBe('DENIED');
    expect(runStatusLabel('RUN_STATE_LOST')).toBe('RUN_STATE_LOST');
    expect(runStatusLabel('NOT_FOUND')).toBe('NOT_FOUND');
    expect(runStatusLabel('RUNNING')).toBe('RUNNING');
    expect(runStatusLabel('SOMETHING_UNKNOWN')).toBe('SOMETHING_UNKNOWN');
  });
});

describe('isTerminalRunStatus', () => {
  it('returns true for terminal statuses', () => {
    expect(isTerminalRunStatus('COMPLETED')).toBe(true);
    expect(isTerminalRunStatus('FAILED')).toBe(true);
    expect(isTerminalRunStatus('CANCELLED')).toBe(true);
    expect(isTerminalRunStatus('DENIED')).toBe(true);
    expect(isTerminalRunStatus('RUN_STATE_LOST')).toBe(true);
    expect(isTerminalRunStatus('NOT_FOUND')).toBe(true);
  });

  it('returns false for non-terminal statuses', () => {
    expect(isTerminalRunStatus('RUNNING')).toBe(false);
    expect(isTerminalRunStatus('AWAITING_APPROVAL')).toBe(false);
    expect(isTerminalRunStatus('SOMETHING_UNKNOWN')).toBe(false);
  });
});

describe('formatRunDuration', () => {
  it('formats a 2m 15s duration', () => {
    expect(formatRunDuration('2024-01-01T00:00:00.000Z', '2024-01-01T00:02:15.000Z')).toBe(
      '2m 15s',
    );
  });

  it('returns n/a when completedAt is null', () => {
    expect(formatRunDuration('2024-01-01T00:00:00.000Z', null)).toBe('n/a');
  });

  it('returns n/a when completedAt is undefined', () => {
    expect(formatRunDuration('2024-01-01T00:00:00.000Z', undefined)).toBe('n/a');
  });

  it('returns 0s for a near-zero boundary case without throwing', () => {
    expect(() =>
      formatRunDuration('2024-01-01T00:00:00.000Z', '2024-01-01T00:00:00.400Z'),
    ).not.toThrow();
    expect(formatRunDuration('2024-01-01T00:00:00.000Z', '2024-01-01T00:00:00.400Z')).toBe('0s');
  });

  it('formats an hour-scale duration', () => {
    expect(formatRunDuration('2024-01-01T00:00:00.000Z', '2024-01-01T01:03:00.000Z')).toBe('1h 3m');
  });
});
