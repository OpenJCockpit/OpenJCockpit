import { useState } from 'react';
import { stopAgentRun, submitApprovalDecision } from '@openjcockpit/api';
import type { ApprovalDecisionKind } from '@openjcockpit/api';
import type { useRunEvents } from './useRunEvents';

type RunEvents = ReturnType<typeof useRunEvents>;

interface Options {
  selectedRunId: string | null;
  approvalContext: RunEvents['approvalContext'];
  setRunStatus: RunEvents['setRunStatus'];
  setApprovalContext: RunEvents['setApprovalContext'];
  setApprovalModalVisible: RunEvents['setApprovalModalVisible'];
  setDecisionError: RunEvents['setDecisionError'];
  onRunEnded?: () => void;
}

/** Stop-run and approval-decision actions for the selected run. */
export function useRunActions({
  selectedRunId,
  approvalContext,
  setRunStatus,
  setApprovalContext,
  setApprovalModalVisible,
  setDecisionError,
  onRunEnded,
}: Options) {
  const [stopping, setStopping] = useState(false);
  const [decisionSubmitting, setDecisionSubmitting] = useState(false);

  async function handleStop() {
    setStopping(true);
    try {
      await stopAgentRun(selectedRunId!);
      setRunStatus('CANCELLED');
      setApprovalModalVisible(false);
      onRunEnded?.();
    } finally {
      setStopping(false);
    }
  }

  async function submitDecision(decision: ApprovalDecisionKind, comment?: string) {
    setDecisionSubmitting(true);
    setDecisionError(null);
    try {
      await submitApprovalDecision(selectedRunId!, {
        decision,
        iteration: approvalContext!.iteration,
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

  return { stopping, decisionSubmitting, handleStop, submitDecision };
}
