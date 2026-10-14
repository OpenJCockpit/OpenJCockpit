import { useEffect, useRef, useState } from 'react';
import type { AgentEvent, AgentRun } from '../../types';
import type {
  ApprovalGateContext,
  WorkflowDefinition,
  WorkflowExecutionPage,
} from '../workflowTypes';
import { getApprovalGateContext, getWorkflowExecution } from '../workflowApi';
import { isTerminalRunStatus } from '../runStatus';

interface Options {
  workflow: WorkflowDefinition | null;
  selectedRunId: string | null;
  showHistory: boolean;
  historyPageRef: React.MutableRefObject<WorkflowExecutionPage | null>;
  mergeHistoryItem: (run: AgentRun) => void;
  refreshHistoryWindow: (workflowId: string) => Promise<void>;
  onRunEnded?: () => void;
}

/**
 * Owns the selected run's detail state (events, status, artifacts, approval gate) and the
 * single polling interval that keeps it, and the history window, fresh.
 */
export function useRunEvents({
  workflow,
  selectedRunId,
  showHistory,
  historyPageRef,
  mergeHistoryItem,
  refreshHistoryWindow,
  onRunEnded,
}: Options) {
  const [latestEvents, setLatestEvents] = useState<Record<string, AgentEvent>>({});
  const [runEvents, setRunEvents] = useState<AgentEvent[]>([]);
  const [runStatus, setRunStatus] = useState<string | null>(null);
  const [artifacts, setArtifacts] = useState<string[]>([]);
  const [failureSummary, setFailureSummary] = useState<string | null>(null);

  const [approvalContext, setApprovalContext] = useState<ApprovalGateContext | null>(null);
  const [approvalModalVisible, setApprovalModalVisible] = useState(false);
  const [decisionError, setDecisionError] = useState<string | null>(null);
  const [waitingAnnouncement, setWaitingAnnouncement] = useState<string | null>(null);
  const previousStatusRef = useRef<string | null>(null);
  const previousWaitingStatusRef = useRef<string | null>(null);
  const tickRef = useRef(0);
  // Lets the single interval (which is only recreated when the workflow changes, not on
  // every selection change) always read the CURRENT selection without needing to be
  // torn down and recreated whenever the operator picks a different history row.
  const selectedRunIdRef = useRef<string | null>(selectedRunId);

  useEffect(() => {
    selectedRunIdRef.current = selectedRunId;
  }, [selectedRunId]);

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

  return {
    latestEvents,
    runEvents,
    runStatus,
    setRunStatus,
    artifacts,
    failureSummary,
    approvalContext,
    setApprovalContext,
    approvalModalVisible,
    setApprovalModalVisible,
    decisionError,
    setDecisionError,
    waitingAnnouncement,
    setWaitingAnnouncement,
    previousWaitingStatusRef,
  };
}
