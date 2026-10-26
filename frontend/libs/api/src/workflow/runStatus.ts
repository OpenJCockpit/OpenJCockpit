import { referenceLabel } from './referenceLabel';
import type {
  AgentCard,
  AgentDefinition,
  AgentEvent,
  OrbCard,
  SequencerNode,
  StatusKind,
} from '../types/types';
import type { WorkflowDefinition } from './workflowTypes';

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

export function buildAgentCards(
  workflow: WorkflowDefinition | null,
  definitions: AgentDefinition[],
  latestEvents: Record<string, AgentEvent>,
): AgentCard[] {
  if (!workflow) return [];
  return workflow.agentIds.map((id) => {
    const definition = definitions.find((d) => d.id === id);
    const event = latestEvents[id];
    const isActive = event?.status === 'RUNNING';
    const isDone = Boolean(event) && !isActive;
    return {
      kind: 'agent',
      id,
      name: definition?.name ?? referenceLabel(id),
      description: definition?.description ?? '',
      status: isActive ? 'Active' : isDone ? 'Done' : 'Waiting',
      statusKind: isActive ? 'active' : isDone ? 'done' : 'waiting',
    };
  });
}

export function buildSequencerNodes(
  workflow: WorkflowDefinition | null,
  agentCards: AgentCard[],
  runEvents: AgentEvent[],
  runStatus: string | null,
  workflowNameById: Map<string, string>,
): SequencerNode[] {
  if (!workflow || !workflow.workflowOrbs || workflow.workflowOrbs.length === 0) {
    return agentCards;
  }
  const orbs = workflow.workflowOrbs;
  const orbEvents = runEvents.filter((e) => e.agentId === 'workflow-orb');
  const consumed = new Set<number>();
  const matches = new Map<number, { event: AgentEvent; kind: 'started' | 'failed' | 'refused' }>();

  orbs.forEach((orb, i) => {
    const name = workflowNameById.get(orb.workflowId) ?? orb.workflowId;
    const startedTitle = `Started child workflow ${name} (${orb.mode})`;
    const failedPrefix = `Child workflow ${orb.workflowId} did not start:`;
    for (let j = 0; j < orbEvents.length; j++) {
      if (consumed.has(j)) continue;
      const e = orbEvents[j];
      if (!e) continue;
      if (e.title === startedTitle && e.evidenceRef.startsWith('run://')) {
        consumed.add(j);
        matches.set(i, { event: e, kind: 'started' });
        break;
      }
      if (e.title.startsWith(failedPrefix)) {
        consumed.add(j);
        matches.set(i, { event: e, kind: 'failed' });
        break;
      }
    }
  });

  orbs.forEach((orb, i) => {
    if (matches.has(i)) return;
    for (let j = 0; j < orbEvents.length; j++) {
      if (consumed.has(j)) continue;
      const e = orbEvents[j];
      if (!e) continue;
      if (e.title.startsWith('Workflow orb refused: ')) {
        consumed.add(j);
        matches.set(i, { event: e, kind: 'refused' });
        break;
      }
    }
  });

  const orbCards: (OrbCard & { placementStage?: string })[] = orbs.map((orb, i) => {
    const referencedWorkflowName = workflowNameById.get(orb.workflowId) ?? orb.workflowId;
    const match = matches.get(i);
    let status = 'Waiting';
    let statusKind: StatusKind = 'waiting';
    let childRunId: string | undefined;
    if (match?.kind === 'failed' || match?.kind === 'refused') {
      status = 'Blocked';
      statusKind = 'changes';
    } else if (match?.kind === 'started') {
      childRunId = match.event.evidenceRef.slice('run://'.length);
      if (orb.mode === 'SEQUENTIAL' && runStatus === 'AWAITING_CHILD_WORKFLOW') {
        status = 'Running';
        statusKind = 'active';
      } else {
        status = 'Done';
        statusKind = 'done';
      }
    }
    return {
      kind: 'orb',
      id: `orb:${i}`,
      referencedWorkflowId: orb.workflowId,
      referencedWorkflowName,
      mode: orb.mode,
      status,
      statusKind,
      childRunId,
      placementStage: orb.placementStage,
    };
  });

  const agentIdSet = new Set(workflow.agentIds);
  // A blank/absent placementStage is genuinely default/unanchored placement and always
  // renders first, as before. A NON-blank placementStage that no longer matches any of
  // workflow.agentIds is an "orphaned anchor". This state cannot arise from the normal
  // create/update API, because WorkflowOrbValidator Rule 5 (anchor-membership) rejects any
  // orb definition whose placementStage does not match a valid agentId at write time. The
  // only reachable paths are (a) direct YAML file editing outside the API, or (b) the bulk
  // /api/workflows/import endpoint, which persists orb-anchor violations as non-blocking
  // warnings instead of rejecting them at write time. Treating an orphaned anchor the same
  // as a genuinely unanchored orb would silently misplace it as if nothing were wrong, so
  // it is rendered last instead, with a visible indicator appended to its status text.
  const unanchoredOrbs = orbCards.filter((o) => !o.placementStage);
  const orphanedOrbs = orbCards
    .filter((o) => o.placementStage && !agentIdSet.has(o.placementStage))
    .map((o) => ({ ...o, status: `${o.status} (anchor stage removed)` }));
  const anchoredOrbs = new Map<string, OrbCard[]>();
  for (const o of orbCards) {
    if (o.placementStage && agentIdSet.has(o.placementStage)) {
      const list = anchoredOrbs.get(o.placementStage) ?? [];
      list.push(o);
      anchoredOrbs.set(o.placementStage, list);
    }
  }
  return [
    ...unanchoredOrbs,
    ...agentCards.flatMap((card) => [card, ...(anchoredOrbs.get(card.id) ?? [])]),
    ...orphanedOrbs,
  ];
}
