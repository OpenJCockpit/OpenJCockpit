import { useMutation } from '@tanstack/react-query';
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
  const stopMutation = useMutation({
    mutationFn: (runId: string) => stopAgentRun(runId),
    onSuccess: () => {
      setRunStatus('CANCELLED');
      setApprovalModalVisible(false);
      onRunEnded?.();
    },
  });

  const decisionMutation = useMutation({
    mutationFn: ({ decision, comment }: { decision: ApprovalDecisionKind; comment?: string }) => {
      if (!selectedRunId || !approvalContext) throw new Error('No approval is pending.');
      return submitApprovalDecision(selectedRunId, {
        decision,
        iteration: approvalContext.iteration,
        comment,
      });
    },
    onMutate: () => setDecisionError(null),
    onSuccess: () => {
      // The run has moved on (resumed, denied, or is re-running with feedback) — the
      // next poll tick reports the new status; the gate itself is no longer open.
      setApprovalModalVisible(false);
      setApprovalContext(null);
    },
    onError: (err) =>
      setDecisionError(
        err instanceof Error ? err.message : 'Failed to submit the approval decision.',
      ),
  });

  // Scoped to the run it was raised for so it clears when another run is selected.
  const stopError =
    stopMutation.isError && stopMutation.variables === selectedRunId
      ? stopMutation.error instanceof Error && stopMutation.error.message
        ? stopMutation.error.message
        : 'Failed to stop the run.'
      : null;

  function handleStop() {
    stopMutation.mutate(selectedRunId!);
  }

  function submitDecision(decision: ApprovalDecisionKind, comment?: string) {
    decisionMutation.mutate({ decision, comment });
  }

  return {
    stopping: stopMutation.isPending,
    stopError,
    decisionSubmitting: decisionMutation.isPending,
    handleStop,
    submitDecision,
  };
}
