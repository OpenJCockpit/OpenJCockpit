import './WorkflowExecution.scss';
import { useEffect, useMemo, useRef, useState } from 'react';
import { loadAgentDefinitions, stopAgentRun } from '../../api';
import type {
  AgentCard,
  AgentDefinition,
  AgentEvent,
  AgentRun,
  OrbCard,
  SequencerNode,
  StatusKind,
} from '../../types';
import type {
  ApprovalDecisionKind,
  ApprovalGateContext,
  WorkflowDefinition,
  WorkflowExecutionPage,
} from '../workflowTypes';
import {
  getApprovalGateContext,
  getWorkflowExecution,
  listWorkflowExecutions,
  listWorkflows,
  submitApprovalDecision,
} from '../workflowApi';
import { AgentSequencer } from '../../components/AgentSequencer/AgentSequencer';
import { AgentLogConsole } from '../../components/AgentLogConsole/AgentLogConsole';
import { StatusChip } from '../../components/StatusChip/StatusChip';
import { ApprovalDecisionModal } from '../ApprovalDecisionModal/ApprovalDecisionModal';
import { WorkflowExecutionHistory } from '../WorkflowExecutionHistory/WorkflowExecutionHistory';
import { isTerminalRunStatus, runStatusKind, runStatusLabel } from '../runStatus';

// 'NOT_FOUND' is retained deliberately: it is what an OLDER backend (pre-dating this
// feature) returns for an unknown run id, so a newer dashboard paired with it still
// terminates instead of polling "waiting" forever (the exact bug this feature fixes
// for 'RUN_STATE_LOST'). Both are non-additive-safe, forward/backward tolerant.
const RUN_LOST_STATUSES = ['RUN_STATE_LOST', 'NOT_FOUND'];
const HISTORY_PAGE_SIZE = 20;
const HISTORY_PAGE_MAX = 100;

interface Props {
  workflow: WorkflowDefinition | null;
  runId: string | null;
  onRunEnded?: () => void;
  /** Called when the operator asks to start the workflow again after its state was lost. */
  onRestartRequested?: () => void;
  showHistory?: boolean;
}

/**
 * A branch entry is always a bare branch name (never a URL) as stored by the backend, so
 * it is rendered as a plain text label. A pull-request entry is always a genuine URL and
 * is rendered as a clickable link.
 */
interface ArtifactEntry {
  label: string;
  url?: string;
}

function parseArtifactLinks(artifacts: string[]): ArtifactEntry[] {
  const entries: ArtifactEntry[] = [];
  for (const artifact of artifacts) {
    if (artifact.startsWith('git-branch:')) {
      const value = artifact.slice('git-branch:'.length).trim();
      if (value) entries.push({ label: `Branch: ${value}` });
    } else if (artifact.startsWith('pull-request:')) {
      const value = artifact.slice('pull-request:'.length).trim();
      if (value) entries.push({ label: 'Pull request', url: value });
    }
  }
  return entries;
}

// Cross-tier constant: mirrors EmbabelOrchestrator.WORKFLOW_DEFINITION_AGENT_ID on the backend,
// and the existing 'workflow-orb' agentId convention already used in this file. A
// workflow-definition read failure is never attributable to one pipeline agent, so its events
// must stay visible in the log console even while another agent is actively RUNNING (BR-11/AC-08).
// Scoped to exactly this one agentId — do NOT broaden this to "any FAILED event", which would
// also un-hide unrelated existing events (e.g. an agentId: 'git' publication-failure event) whose
// visibility nobody asked to change.
const DEFINITION_FAILURE_AGENT_ID = 'workflow-definition';

export function WorkflowExecution({
  workflow,
  runId,
  onRunEnded,
  onRestartRequested,
  showHistory = true,
}: Props) {
  const [definitions, setDefinitions] = useState<AgentDefinition[]>([]);
  const [latestEvents, setLatestEvents] = useState<Record<string, AgentEvent>>({});
  const [runEvents, setRunEvents] = useState<AgentEvent[]>([]);
  const [runStatus, setRunStatus] = useState<string | null>(null);
  const [artifacts, setArtifacts] = useState<string[]>([]);
  const [failureSummary, setFailureSummary] = useState<string | null>(null);
  const [stopping, setStopping] = useState(false);

  const [approvalContext, setApprovalContext] = useState<ApprovalGateContext | null>(null);
  const [approvalModalVisible, setApprovalModalVisible] = useState(false);
  const [decisionSubmitting, setDecisionSubmitting] = useState(false);
  const [decisionError, setDecisionError] = useState<string | null>(null);
  const [waitingAnnouncement, setWaitingAnnouncement] = useState<string | null>(null);
  const previousStatusRef = useRef<string | null>(null);
  const previousWaitingStatusRef = useRef<string | null>(null);

  // Container state: which run is currently selected (seeded from the runId prop when
  // a "just started" flow hands one in), and the workflow's execution history page.
  const [selectedRunId, setSelectedRunId] = useState<string | null>(runId);
  const [historyPage, setHistoryPage] = useState<WorkflowExecutionPage | null>(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const tickRef = useRef(0);
  const historyPageRef = useRef<WorkflowExecutionPage | null>(null);
  // Lets the single interval (which is only recreated when the workflow changes, not on
  // every selection change) always read the CURRENT selection without needing to be
  // torn down and recreated whenever the operator picks a different history row.
  const selectedRunIdRef = useRef<string | null>(selectedRunId);

  useEffect(() => {
    historyPageRef.current = historyPage;
  }, [historyPage]);

  useEffect(() => {
    selectedRunIdRef.current = selectedRunId;
  }, [selectedRunId]);

  useEffect(() => {
    void loadAgentDefinitions().then(setDefinitions);
  }, []);

  const [allWorkflows, setAllWorkflows] = useState<WorkflowDefinition[]>([]);
  useEffect(() => {
    void listWorkflows()
      .then(setAllWorkflows)
      .catch(() => setAllWorkflows([])); // display-name lookup only (workflowNameById); [] degrades an orb label to its raw id, no functional loss
  }, []);

  // Keep selectedRunId in sync with a newly-provided runId prop (e.g. a fresh
  // "start workflow" flow), without clobbering a user's in-history selection when the
  // prop hasn't changed.
  useEffect(() => {
    setSelectedRunId(runId);
  }, [runId]);

  function mergeHistoryItem(run: AgentRun) {
    if (!run.workflowId) return;
    setHistoryPage((prev) => {
      if (!prev) return prev;
      const exists = prev.items.some((item) => item.runId === run.runId);
      if (!exists) return prev;
      return {
        ...prev,
        items: prev.items.map((item) =>
          item.runId === run.runId
            ? {
                ...item,
                status: run.status,
                completedAt: run.completedAt ?? null,
                durationMillis:
                  run.completedAt && item.startedAt
                    ? new Date(run.completedAt).getTime() - new Date(item.startedAt).getTime()
                    : item.durationMillis,
              }
            : item,
        ),
      };
    });
  }

  // Merges a freshly-fetched page into the existing one by runId, preserving the order
  // and identity of every already-loaded row (so a background refresh never truncates,
  // duplicates, or reorders rows gained via "Load more"). Rows in `next` that are not
  // yet loaded (e.g. a brand new run) are appended after the existing ones. `total` and
  // `hasMore` are adopted from `next` since those reflect the freshest server state.
  function mergeHistoryPage(
    prev: WorkflowExecutionPage | null,
    next: WorkflowExecutionPage,
  ): WorkflowExecutionPage {
    if (!prev) return next;
    const nextById = new Map(next.items.map((item) => [item.runId, item]));
    const mergedExisting = prev.items.map((item) => nextById.get(item.runId) ?? item);
    const existingIds = new Set(prev.items.map((item) => item.runId));
    const newlyAppeared = next.items.filter((item) => !existingIds.has(item.runId));
    return {
      ...next,
      items: [...mergedExisting, ...newlyAppeared],
    };
  }

  async function fetchHistory(workflowId: string) {
    setHistoryLoading(true);
    try {
      const page = await listWorkflowExecutions(workflowId, {
        limit: HISTORY_PAGE_SIZE,
        offset: 0,
      });
      setHistoryPage(page);
      setHistoryError(null);
    } catch (err) {
      setHistoryError(err instanceof Error ? err.message : 'Failed to load execution history');
    } finally {
      setHistoryLoading(false);
    }
  }

  // Background/periodic refresh used by the polling interval: re-fetches a window
  // covering everything currently loaded (clamped to the server max of 100) and MERGES
  // the result in place, instead of fetchHistory's plain "replace with the first page"
  // behavior (which is still correct for the initial load and the manual Retry button,
  // both of which keep calling fetchHistory unchanged).
  async function refreshHistoryWindow(workflowId: string) {
    const currentCount = historyPageRef.current?.items.length ?? 0;
    const limit = Math.min(Math.max(currentCount, HISTORY_PAGE_SIZE), HISTORY_PAGE_MAX);
    try {
      const page = await listWorkflowExecutions(workflowId, { limit, offset: 0 });
      setHistoryPage((prev) => mergeHistoryPage(prev, page));
      setHistoryError(null);
    } catch {
      // Keep showing the previously loaded history; a transient failure here is quietly
      // retried on the next eligible tick rather than surfacing an error banner.
    }
  }

  useEffect(() => {
    if (!workflow) {
      setHistoryPage(null);
      setHistoryError(null);
      return;
    }
    if (showHistory) {
      void fetchHistory(workflow.id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- re-fetch only when the workflow identity changes
  }, [workflow?.id]);

  async function fetchApprovalContextFor(runIdForContext: string) {
    try {
      const context = await getApprovalGateContext(runIdForContext);
      setApprovalContext(context);
      setApprovalModalVisible(true);
      setDecisionError(null);
    } catch (err) {
      console.warn('Failed to load approval gate context', err);
    }
  }

  // Fetches and applies the detail for `currentRunId`. Used both for the immediate
  // fetch-on-selection (so a finished run never flashes as "RUNNING" while waiting for
  // the next tick) and for every interval tick while a run is selected. Ignores its own
  // result if the selection has since moved on to a different run.
  async function fetchRunDetail(workflowId: string, currentRunId: string) {
    try {
      const run = await getWorkflowExecution(workflowId, currentRunId);
      if (selectedRunIdRef.current !== currentRunId) return;
      const byAgent: Record<string, AgentEvent> = {};
      for (const event of run.events) {
        byAgent[event.agentId] = event;
      }
      setLatestEvents(byAgent);
      setRunEvents(run.events);
      setRunStatus(run.status);
      setArtifacts(run.generatedArtifacts);
      setFailureSummary(run.failureSummary ?? null);
      mergeHistoryItem(run);

      if (run.status === 'AWAITING_APPROVAL' && previousStatusRef.current !== 'AWAITING_APPROVAL') {
        void fetchApprovalContextFor(currentRunId);
      }
      previousStatusRef.current = run.status;
      if (isTerminalRunStatus(run.status)) {
        onRunEnded?.();
      }
    } catch (err) {
      if (selectedRunIdRef.current !== currentRunId) return;
      if ((err as { status?: number }).status === 404) {
        setRunStatus('RUN_STATE_LOST');
        previousStatusRef.current = 'RUN_STATE_LOST';
      }
      // keep polling on transient errors
    }
  }

  // Resets/seeds per-run detail state whenever the selection changes. Deliberately
  // separate from the interval-owning effect below so that switching or clearing the
  // selected run never tears down and recreates the single polling interval.
  useEffect(() => {
    if (!selectedRunId) {
      setLatestEvents({});
      setRunEvents([]);
      setRunStatus(null);
      setArtifacts([]);
      setFailureSummary(null);
      setApprovalContext(null);
      setApprovalModalVisible(false);
      setDecisionError(null);
      setWaitingAnnouncement(null);
      previousStatusRef.current = null;
      previousWaitingStatusRef.current = null;
      return;
    }
    if (!workflow) return;
    const workflowId = workflow.id;

    setLatestEvents({});
    setRunEvents([]);
    setArtifacts([]);
    setFailureSummary(null);
    setApprovalContext(null);
    setApprovalModalVisible(false);
    setDecisionError(null);
    setWaitingAnnouncement(null);
    previousStatusRef.current = 'RUNNING';
    previousWaitingStatusRef.current = 'RUNNING';
    tickRef.current = 0;

    // Seed from the already-known summary row for this run (from the currently-loaded
    // history page) instead of hard-coding 'RUNNING', so a COMPLETED/FAILED/CANCELLED
    // run opened from history never flashes as "RUNNING". Falls back to 'RUNNING' only
    // when the row truly isn't loaded yet (e.g. a brand-new run just started, which
    // legitimately is running and isn't in history yet).
    const knownRow = historyPageRef.current?.items.find((item) => item.runId === selectedRunId);
    const seededStatus = knownRow?.status ?? 'RUNNING';
    setRunStatus(seededStatus);
    previousStatusRef.current = null;

    // Fetch immediately rather than waiting for the next 1s timer tick.
    void fetchRunDetail(workflowId, selectedRunId);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- intentionally re-runs only on selectedRunId/workflow change
  }, [selectedRunId, workflow]);

  // The single polling interval for this container. Its lifetime tracks the workflow
  // (not the selection), so history-list refresh keeps running even when no run is
  // selected (F-3). The per-tick callback reads the CURRENT selection via
  // selectedRunIdRef and only does detail work when one is set.
  useEffect(() => {
    if (!workflow) return;
    const workflowId = workflow.id;

    const interval = setInterval(async () => {
      tickRef.current += 1;
      const isEvenTick = tickRef.current % 2 === 0;

      const currentRunId = selectedRunIdRef.current;
      if (currentRunId) {
        // Only poll detail while the selected run is non-terminal.
        const detailIsTerminal =
          previousStatusRef.current !== null && isTerminalRunStatus(previousStatusRef.current);
        if (!detailIsTerminal) {
          await fetchRunDetail(workflowId, currentRunId);
        }
      }

      // On every second tick, also refresh the history window, but only while at least
      // one currently-loaded row is non-terminal (avoids pointless requests once
      // everything visible has settled). This runs regardless of whether a run is
      // currently selected for detail viewing.
      if (showHistory) {
        const hasLiveHistoryRow = (historyPageRef.current?.items ?? []).some(
          (item) => !isTerminalRunStatus(item.status),
        );
        if (isEvenTick && hasLiveHistoryRow) {
          void refreshHistoryWindow(workflowId);
        }
      }
    }, 1000);

    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- interval keyed only on the workflow identity; current selection is read via selectedRunIdRef
  }, [workflow?.id]);

  const agentCards = useMemo<AgentCard[]>(() => {
    if (!workflow) return [];
    return workflow.agentIds.map((id) => {
      const definition = definitions.find((d) => d.id === id);
      const event = latestEvents[id];
      const isActive = event?.status === 'RUNNING';
      const isDone = Boolean(event) && !isActive;
      return {
        kind: 'agent',
        id,
        name: definition?.name ?? id,
        description: definition?.description ?? '',
        status: isActive ? 'Active' : isDone ? 'Done' : 'Waiting',
        statusKind: isActive ? 'active' : isDone ? 'done' : 'waiting',
      };
    });
  }, [workflow, definitions, latestEvents]);

  const workflowNameById = useMemo(
    () => new Map(allWorkflows.map((w) => [w.id, w.name])),
    [allWorkflows],
  );

  const sequencerNodes = useMemo<SequencerNode[]>(() => {
    if (!workflow || !workflow.workflowOrbs || workflow.workflowOrbs.length === 0) {
      return agentCards;
    }
    const orbs = workflow.workflowOrbs;
    const orbEvents = runEvents.filter((e) => e.agentId === 'workflow-orb');
    const consumed = new Set<number>();
    const matches = new Map<
      number,
      { event: AgentEvent; kind: 'started' | 'failed' | 'refused' }
    >();

    orbs.forEach((orb, i) => {
      const name = workflowNameById.get(orb.workflowId) ?? orb.workflowId;
      const startedTitle = `Started child workflow ${name} (${orb.mode})`;
      const failedPrefix = `Child workflow ${orb.workflowId} did not start:`;
      for (let j = 0; j < orbEvents.length; j++) {
        if (consumed.has(j)) continue;
        const e = orbEvents[j];
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
  }, [workflow, agentCards, runEvents, runStatus, workflowNameById]);

  const activeAgent = useMemo(
    () => agentCards.find((agent) => agent.statusKind === 'active') ?? null,
    [agentCards],
  );

  const logEvents = useMemo(() => {
    if (!activeAgent) return runEvents;
    return runEvents.filter(
      (event) => event.agentId === activeAgent.id || event.agentId === DEFINITION_FAILURE_AGENT_ID,
    );
  }, [runEvents, activeAgent]);

  const definitionFailureEvent = useMemo(
    () => runEvents.find((e) => e.agentId === DEFINITION_FAILURE_AGENT_ID) ?? null,
    [runEvents],
  );

  const artifactLinks = useMemo(() => parseArtifactLinks(artifacts), [artifacts]);

  const latestChildWorkflowEvent = useMemo(() => {
    const matches = runEvents.filter(
      (event) => event.agentId === 'workflow-orb' && event.evidenceRef.startsWith('run://'),
    );
    return matches.length > 0 ? matches[matches.length - 1] : null;
  }, [runEvents]);

  const childRunId = latestChildWorkflowEvent
    ? latestChildWorkflowEvent.evidenceRef.slice('run://'.length)
    : null;

  useEffect(() => {
    const previous = previousWaitingStatusRef.current;
    if (runStatus === 'AWAITING_CHILD_WORKFLOW' && previous !== 'AWAITING_CHILD_WORKFLOW') {
      setWaitingAnnouncement(
        latestChildWorkflowEvent
          ? `Waiting for child workflow: ${latestChildWorkflowEvent.title}`
          : 'Waiting for a triggered child workflow to complete.',
      );
    } else if (previous === 'AWAITING_CHILD_WORKFLOW' && runStatus !== 'AWAITING_CHILD_WORKFLOW') {
      setWaitingAnnouncement('No longer waiting for the child workflow.');
    }
    previousWaitingStatusRef.current = runStatus;
  }, [runStatus, latestChildWorkflowEvent]);

  const isRunLost = runStatus !== null && RUN_LOST_STATUSES.includes(runStatus);

  async function handleLoadMore() {
    if (!workflow) return;
    setHistoryLoading(true);
    try {
      const nextOffset = historyPage?.items.length ?? 0;
      const nextPage = await listWorkflowExecutions(workflow.id, {
        limit: HISTORY_PAGE_SIZE,
        offset: nextOffset,
      });
      setHistoryPage((prev) =>
        prev
          ? {
              ...nextPage,
              items: [...prev.items, ...nextPage.items],
            }
          : nextPage,
      );
      setHistoryError(null);
    } catch (err) {
      setHistoryError(err instanceof Error ? err.message : 'Failed to load execution history');
    } finally {
      setHistoryLoading(false);
    }
  }

  async function handleStop() {
    if (!selectedRunId) return;
    setStopping(true);
    try {
      await stopAgentRun(selectedRunId);
      setRunStatus('CANCELLED');
      setApprovalModalVisible(false);
      onRunEnded?.();
    } finally {
      setStopping(false);
    }
  }

  async function submitDecision(decision: ApprovalDecisionKind, comment?: string) {
    if (!selectedRunId || !approvalContext) return;
    setDecisionSubmitting(true);
    setDecisionError(null);
    try {
      await submitApprovalDecision(selectedRunId, {
        decision,
        iteration: approvalContext.iteration,
        comment,
      });
      // The run has moved on (resumed, denied, or is re-running with feedback) — the
      // next poll tick reports the new status; the gate itself is no longer open.
      setApprovalModalVisible(false);
      setApprovalContext(null);
    } catch (err) {
      setDecisionError(
        err instanceof Error ? err.message : 'Failed to submit the approval decision.',
      );
    } finally {
      setDecisionSubmitting(false);
    }
  }

  if (!workflow) {
    return (
      <p className="settings-desc workflow-execution-empty">
        Start a workflow from the Overview tab to see it running here.
      </p>
    );
  }

  return (
    <section className="panel workflow-execution-panel">
      <header className="workflow-execution-header">
        <div>
          <span className="eyebrow">{workflow.id}</span>
          <h2>{workflow.name}</h2>
        </div>
        <div className="workflow-execution-actions">
          {runStatus && (
            <StatusChip label={runStatusLabel(runStatus)} kind={runStatusKind(runStatus)} />
          )}
          {selectedRunId && !isTerminalRunStatus(runStatus ?? '') && (
            <button
              className="button button--stop"
              onClick={() => void handleStop()}
              disabled={stopping}
            >
              {stopping ? '⟳ Stopping…' : '◼ Stop'}
            </button>
          )}
        </div>
      </header>

      {showHistory && (
        <WorkflowExecutionHistory
          page={historyPage}
          loading={historyLoading}
          error={historyError}
          selectedRunId={selectedRunId}
          onSelect={(runId) => setSelectedRunId(runId)}
          onRetry={() => void fetchHistory(workflow.id)}
          onLoadMore={() => void handleLoadMore()}
        />
      )}

      {!selectedRunId && (
        <p className="settings-desc workflow-execution-empty">
          No active execution for this workflow. Start it from the Overview tab or select one from
          the history above to watch it here.
        </p>
      )}

      {selectedRunId && isRunLost && (
        <div className="workflow-run-lost-panel" role="alert">
          <p>
            The state of this run was lost, likely because the service restarted. No approval
            decision is possible for this run.
          </p>
          {onRestartRequested && (
            <button className="button button--start" onClick={onRestartRequested}>
              Start again
            </button>
          )}
        </div>
      )}

      {selectedRunId && !isRunLost && artifactLinks.length > 0 && (
        <div className="workflow-artifacts">
          {artifactLinks.map((entry) =>
            entry.url ? (
              <a key={entry.label} href={entry.url} target="_blank" rel="noopener noreferrer">
                {entry.label}
              </a>
            ) : (
              <span key={entry.label} className="workflow-artifact-label">
                {entry.label}
              </span>
            ),
          )}
        </div>
      )}

      {selectedRunId && !isRunLost && latestChildWorkflowEvent && childRunId && (
        <p className="workflow-child-info">
          {latestChildWorkflowEvent.title}
          {' — '}
          Child run: <code>{childRunId}</code>
        </p>
      )}

      {waitingAnnouncement && (
        <p aria-live="polite" role="status" className="workflow-waiting-announcement">
          {waitingAnnouncement}
        </p>
      )}

      {definitionFailureEvent && (
        <p aria-live="polite" role="status" className="workflow-waiting-announcement">
          {definitionFailureEvent.title}
        </p>
      )}

      {failureSummary && (
        <p aria-live="polite" role="status" className="workflow-waiting-announcement">
          {failureSummary}
        </p>
      )}

      {selectedRunId &&
        runStatus === 'AWAITING_APPROVAL' &&
        approvalContext &&
        !approvalModalVisible && (
          <button className="button button--workflow" onClick={() => setApprovalModalVisible(true)}>
            Review pending approval
          </button>
        )}

      {selectedRunId && !isRunLost && (
        <AgentSequencer
          nodes={sequencerNodes}
          definitions={definitions}
          latestEvents={latestEvents}
        />
      )}

      {selectedRunId && !isRunLost && (
        <AgentLogConsole
          events={logEvents}
          agentName={activeAgent?.name ?? null}
          isActive={Boolean(activeAgent)}
        />
      )}

      {selectedRunId &&
        runStatus === 'AWAITING_APPROVAL' &&
        approvalContext &&
        approvalModalVisible && (
          <ApprovalDecisionModal
            context={approvalContext}
            submitting={decisionSubmitting}
            errorMessage={decisionError}
            onAccept={() => void submitDecision('ACCEPT')}
            onDeny={() => void submitDecision('DENY')}
            onAcceptWithComments={(comment) => void submitDecision('ACCEPT_WITH_COMMENTS', comment)}
            onDismiss={() => setApprovalModalVisible(false)}
          />
        )}
    </section>
  );
}
