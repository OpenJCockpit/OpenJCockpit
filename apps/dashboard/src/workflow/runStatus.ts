import type { StatusKind } from '../types';

// Single source of truth for run-status -> kind/label/terminal-classification,
// replacing two previously-divergent copies (WorkflowExecution.tsx's
// runStatusKind/runStatusLabel/TERMINAL_STATUSES and WorkflowOverview.tsx's
// statusKindFor). WorkflowExecution.tsx's mapping is canonical here.
//
// 'RUN_STATE_LOST' and 'NOT_FOUND' are retained as terminal deliberately: they
// are what an OLDER backend (pre-dating the workflow-execution-history
// feature) returns for an unknown run id, so a newer dashboard paired with it
// still terminates instead of polling "waiting" forever.
//
// 'TIMED_OUT' and 'BLOCKED' (workflow-trigger-workflow-orb: a parent that gave
// up waiting on, or was refused starting, a child workflow) are also terminal
// — such a run will never progress further. 'AWAITING_CHILD_WORKFLOW' is
// explicitly NOT terminal — a parent parked waiting on a child must keep
// polling.
export enum TerminalRunStatus {
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  CANCELLED = 'CANCELLED',
  DENIED = 'DENIED',
  RUN_STATE_LOST = 'RUN_STATE_LOST',
  NOT_FOUND = 'NOT_FOUND',
  TIMED_OUT = 'TIMED_OUT',
  BLOCKED = 'BLOCKED',
}

export function isTerminalRunStatus(status: string): boolean {
  return (Object.values(TerminalRunStatus) as string[]).includes(status);
}

export function runStatusKind(status: string): StatusKind {
  switch (status) {
    case 'COMPLETED':
      return 'ok';
    case 'FAILED':
    case 'CANCELLED':
    case 'DENIED':
    case 'RUN_STATE_LOST':
    case 'NOT_FOUND':
    case 'TIMED_OUT':
    case 'BLOCKED':
      return 'changes';
    case 'AWAITING_APPROVAL':
    case 'AWAITING_CHILD_WORKFLOW':
      return 'waiting';
    case 'RUNNING':
      return 'active';
    default:
      return 'waiting';
  }
}

export function runStatusLabel(status: string): string {
  if (status === 'AWAITING_APPROVAL') return 'Awaiting approval';
  if (status === 'AWAITING_CHILD_WORKFLOW') return 'Waiting for child workflow';
  if (status === 'TIMED_OUT') return 'Timed out';
  if (status === 'BLOCKED') return 'Blocked';
  return status;
}

// Renders 'n/a' (matching this codebase's existing "n/a" placeholder convention
// for missing optional values) when the run is still in progress. Never derives
// a fake elapsed time from `now` for a still-running execution — a running
// execution's duration must never look like a completed duration.
export function formatRunDuration(
  startedAt: string | Date,
  completedAt: string | Date | null | undefined,
): string {
  if (completedAt === null || completedAt === undefined) return 'n/a';
  const start = new Date(startedAt).getTime();
  const end = new Date(completedAt).getTime();
  const totalMs = Math.max(0, end - start);
  const totalSeconds = Math.floor(totalMs / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m ${seconds}s`;
  return `${seconds}s`;
}
