import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  buildAgentCards,
  buildSequencerNodes,
  isTerminalRunStatus,
  listWorkflows,
  queryKeys,
  runStatusKind,
  runStatusLabel,
} from '@openjcockpit/api';
import type { AgentDefinition, WorkflowDefinition } from '@openjcockpit/api';
import { loadLinkableAgents } from '../linkableAgents';
import { AgentSequencer } from '../AgentSequencer/AgentSequencer';
import { AgentLogConsole } from '../AgentLogConsole/AgentLogConsole';
import {
  StatusChip,
  Eyebrow,
  Panel,
  fieldHintClass,
  fieldErrorClass,
  cx,
  Button,
} from '@openjcockpit/ui';
import { ApprovalDecisionModal } from '../ApprovalDecisionModal/ApprovalDecisionModal';
import { WorkflowExecutionHistory } from '../WorkflowExecutionHistory/WorkflowExecutionHistory';
import { ArtifactList, parseArtifactLinks } from './ArtifactList';
import { RunLostPanel } from './RunLostPanel';
import { RunStatusAnnouncements } from './RunStatusAnnouncements';
import { useRunHistory } from './useRunHistory';
import { useRunActions } from './useRunActions';
import { useRunEvents } from './useRunEvents';
import styles from './WorkflowExecution.module.scss';

// 'NOT_FOUND' is retained deliberately: it is what an OLDER backend (pre-dating this
// feature) returns for an unknown run id, so a newer dashboard paired with it still
// terminates instead of polling "waiting" forever (the exact bug this feature fixes
// for 'RUN_STATE_LOST'). Both are non-additive-safe, forward/backward tolerant.
const NO_DEFINITIONS: AgentDefinition[] = [];
const NO_WORKFLOWS: WorkflowDefinition[] = [];

const RUN_LOST_STATUSES = ['RUN_STATE_LOST', 'NOT_FOUND'];

interface Props {
  workflow: WorkflowDefinition | null;
  runId: string | null;
  onRunEnded?: () => void;
  /** Called when the operator asks to start the workflow again after its state was lost. */
  onRestartRequested?: () => void;
  showHistory?: boolean;
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
  // Container state: which run is currently selected (seeded from the runId prop when
  // a "just started" flow hands one in).
  const [selectedRunId, setSelectedRunId] = useState<string | null>(runId);

  const {
    historyPage,
    historyLoading,
    historyError,
    historyPageRef,
    fetchHistory,
    refreshHistoryWindow,
    mergeHistoryItem,
    loadMore,
  } = useRunHistory(workflow, showHistory);

  const {
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
    approvalLoadError,
    waitingAnnouncement,
    setWaitingAnnouncement,
    previousWaitingStatus,
    setPreviousWaitingStatus,
  } = useRunEvents({
    workflow,
    selectedRunId,
    showHistory,
    historyPageRef,
    mergeHistoryItem,
    refreshHistoryWindow,
    onRunEnded,
  });

  const { data: definitions = NO_DEFINITIONS } = useQuery({
    queryKey: queryKeys.linkableAgents,
    queryFn: loadLinkableAgents, // never rejects: a failed catalog degrades to []
  });

  // display-name lookup only (workflowNameById); [] degrades an orb label to its raw id, no functional loss
  const { data: allWorkflows = NO_WORKFLOWS } = useQuery({
    queryKey: queryKeys.workflows,
    queryFn: ({ signal }) => listWorkflows(signal),
  });

  // Keep selectedRunId in sync with a newly-provided runId prop (e.g. a fresh
  // "start workflow" flow), without clobbering a user's in-history selection when the
  // prop hasn't changed.
  const [prevRunId, setPrevRunId] = useState(runId);
  if (prevRunId !== runId) {
    setPrevRunId(runId);
    setSelectedRunId(runId);
  }

  const agentCards = useMemo(
    () => buildAgentCards(workflow, definitions, latestEvents),
    [workflow, definitions, latestEvents],
  );

  const workflowNameById = useMemo(
    () => new Map(allWorkflows.map((w) => [w.id, w.name])),
    [allWorkflows],
  );

  const sequencerNodes = useMemo(
    () => buildSequencerNodes(workflow, agentCards, runEvents, runStatus, workflowNameById),
    [workflow, agentCards, runEvents, runStatus, workflowNameById],
  );

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

  // Derived during render (not in an effect): announce only on entering/leaving the waiting state.
  if (previousWaitingStatus !== runStatus) {
    setPreviousWaitingStatus(runStatus);
    if (
      runStatus === 'AWAITING_CHILD_WORKFLOW' &&
      previousWaitingStatus !== 'AWAITING_CHILD_WORKFLOW'
    ) {
      setWaitingAnnouncement(
        latestChildWorkflowEvent
          ? `Waiting for child workflow: ${latestChildWorkflowEvent.title}`
          : 'Waiting for a triggered child workflow to complete.',
      );
    } else if (previousWaitingStatus === 'AWAITING_CHILD_WORKFLOW') {
      setWaitingAnnouncement('No longer waiting for the child workflow.');
    }
  }

  const { stopping, stopError, decisionSubmitting, handleStop, submitDecision } = useRunActions({
    selectedRunId,
    approvalContext,
    setRunStatus,
    setApprovalContext,
    setApprovalModalVisible,
    setDecisionError,
    onRunEnded,
  });

  const isRunLost = runStatus !== null && RUN_LOST_STATUSES.includes(runStatus);

  if (!workflow) {
    return (
      <p className={cx(fieldHintClass, styles.workflowExecutionEmpty)}>
        Start a workflow from the Overview tab to see it running here.
      </p>
    );
  }

  return (
    <Panel className={styles.workflowExecutionPanel}>
      <header className={styles.workflowExecutionHeader}>
        <div>
          <Eyebrow>{workflow.id}</Eyebrow>
          <h2>{workflow.name}</h2>
        </div>
        <div className={styles.workflowExecutionActions}>
          {runStatus && (
            <StatusChip label={runStatusLabel(runStatus)} kind={runStatusKind(runStatus)} />
          )}
          {selectedRunId && !isTerminalRunStatus(runStatus ?? '') && (
            <Button variant="danger" onClick={() => handleStop(selectedRunId)} disabled={stopping}>
              {stopping ? '⟳ Stopping…' : '◼ Stop'}
            </Button>
          )}
        </div>
      </header>

      {showHistory && (
        <WorkflowExecutionHistory
          className={styles.workflowExecutionHistorySlot}
          page={historyPage}
          loading={historyLoading}
          error={historyError}
          selectedRunId={selectedRunId}
          onSelect={(runId) => setSelectedRunId(runId)}
          onRetry={() => void fetchHistory(workflow.id)}
          onLoadMore={() => void loadMore()}
        />
      )}

      {!selectedRunId && (
        <p className={cx(fieldHintClass, styles.workflowExecutionEmpty)}>
          No active execution for this workflow. Start it from the Overview tab or select one from
          the history above to watch it here.
        </p>
      )}

      {selectedRunId && isRunLost && <RunLostPanel onRestartRequested={onRestartRequested} />}

      {selectedRunId && !isRunLost && <ArtifactList entries={artifactLinks} />}

      {selectedRunId && !isRunLost && latestChildWorkflowEvent && childRunId && (
        <p className="workflow-child-info">
          {latestChildWorkflowEvent.title}
          {' — '}
          Child run: <code>{childRunId}</code>
        </p>
      )}

      <RunStatusAnnouncements
        messages={[waitingAnnouncement, definitionFailureEvent?.title, failureSummary]}
      />

      {stopError && (
        <p className={fieldErrorClass} role="alert">
          {stopError}
        </p>
      )}

      {approvalLoadError && (
        <p className={fieldErrorClass} role="alert">
          {approvalLoadError}
        </p>
      )}

      {selectedRunId &&
        runStatus === 'AWAITING_APPROVAL' &&
        approvalContext &&
        !approvalModalVisible && (
          <Button variant="workflow" onClick={() => setApprovalModalVisible(true)}>
            Review pending approval
          </Button>
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
    </Panel>
  );
}
