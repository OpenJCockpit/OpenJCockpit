import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { WorkflowExecution } from './WorkflowExecution';
import * as api from '../../api';
import * as workflowApi from '../workflowApi';
import type {
  ApprovalGateContext,
  WorkflowDefinition,
  WorkflowExecutionPage,
} from '../workflowTypes';
import type { AgentRun } from '../../types';

vi.mock('../../api');
vi.mock('../workflowApi', async () => {
  const actual = await vi.importActual<typeof import('../workflowApi')>('../workflowApi');
  return {
    ...actual,
    getApprovalGateContext: vi.fn(),
    submitApprovalDecision: vi.fn(),
    listWorkflowExecutions: vi.fn(),
    getWorkflowExecution: vi.fn(),
    listWorkflows: vi.fn(),
  };
});

const WORKFLOW: WorkflowDefinition = {
  id: 'wf-1',
  name: 'Customer Onboarding',
  projectName: 'Noordzee Logistics B.V.',
  description: 'desc',
  agentIds: ['requirement', 'evidence'],
  subagentNames: [],
  skillNames: [],
  mcpTools: [],
  trigger: { dashboardButtonEnabled: true, hermesSignalEnabled: false, fileDeliveryEnabled: false },
  execution: { customerId: 'noordzee-logistics', repositoryUrl: '', timeoutSeconds: 600 },
  status: 'ACTIVE',
};

function emptyHistoryPage(): WorkflowExecutionPage {
  return { items: [], limit: 20, offset: 0, total: 0, hasMore: false };
}

function runWithStatus(status: string, overrides: Partial<AgentRun> = {}): AgentRun {
  return {
    runId: 'run-1',
    customerId: 'noordzee-logistics',
    specFile: '',
    repositoryUrl: '',
    status,
    startedAt: new Date().toISOString(),
    events: [],
    generatedArtifacts: [],
    workflowId: 'wf-1',
    startedBy: null,
    completedAt: null,
    ...overrides,
  };
}

describe('WorkflowExecution', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.loadAgentDefinitions).mockResolvedValue([
      {
        id: 'requirement',
        name: 'Requirement Agent',
        description: 'Extracts requirements',
        role: 'requirement',
        sequenceOrder: 1,
        inputType: '',
        outputType: '',
      },
      {
        id: 'evidence',
        name: 'Evidence Agent',
        description: 'Collects evidence',
        role: 'evidence',
        sequenceOrder: 2,
        inputType: '',
        outputType: '',
      },
    ]);
    vi.mocked(workflowApi.listWorkflowExecutions).mockResolvedValue(emptyHistoryPage());
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([]);
  });

  it('shows an empty state when no workflow has been opened yet', async () => {
    await act(async () => {
      render(<WorkflowExecution workflow={null} runId={null} />);
    });

    expect(screen.getByText(/Start a workflow from the Overview tab/)).toBeInTheDocument();
  });

  it('shows a not-running state when a workflow is opened without an active run', async () => {
    await act(async () => {
      render(<WorkflowExecution workflow={WORKFLOW} runId={null} />);
    });

    expect(screen.getByText('Customer Onboarding')).toBeInTheDocument();
    expect(screen.getByText(/No active execution for this workflow/)).toBeInTheDocument();
  });

  it('shows a Stop button and RUNNING status as soon as a run is opened', async () => {
    vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(runWithStatus('RUNNING'));

    await act(async () => {
      render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
    });

    expect(screen.getByText('◼ Stop')).toBeInTheDocument();
    expect(screen.getByText('RUNNING')).toBeInTheDocument();
  });

  it('still renders the run status when listWorkflows rejects', async () => {
    vi.mocked(workflowApi.listWorkflows).mockRejectedValue(new Error('boom'));
    vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(runWithStatus('RUNNING'));

    await act(async () => {
      render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
    });

    await waitFor(
      () => expect(workflowApi.getWorkflowExecution).toHaveBeenCalledWith('wf-1', 'run-1'),
      { timeout: 3000 },
    );
    expect(screen.getByText('RUNNING')).toBeInTheDocument();
  });

  it('polls the run and renders the agent sequencer while active', async () => {
    vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(
      runWithStatus('RUNNING', {
        events: [
          {
            timestamp: new Date().toISOString(),
            agentId: 'requirement',
            title: 'Working',
            status: 'RUNNING',
            evidenceRef: '',
          },
        ],
      }),
    );

    await act(async () => {
      render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
    });

    await waitFor(
      () => expect(workflowApi.getWorkflowExecution).toHaveBeenCalledWith('wf-1', 'run-1'),
      { timeout: 3000 },
    );
    expect(await screen.findByText('Requirement Agent')).toBeInTheDocument();
    expect(screen.getByText('Evidence Agent')).toBeInTheDocument();
  });

  it('shows the active agent log console with its latest event', async () => {
    vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(
      runWithStatus('RUNNING', {
        events: [
          {
            timestamp: new Date().toISOString(),
            agentId: 'requirement',
            title: 'Extracting requirements',
            status: 'RUNNING',
            evidenceRef: '',
          },
        ],
      }),
    );

    await act(async () => {
      render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
    });

    await waitFor(
      () => expect(workflowApi.getWorkflowExecution).toHaveBeenCalledWith('wf-1', 'run-1'),
      { timeout: 3000 },
    );
    const logTextarea = await screen.findByLabelText<HTMLTextAreaElement>('Agent execution log');
    expect(logTextarea.value).toContain('Extracting requirements');
    expect(screen.getByText(/AGENT LOG \/\/ Requirement Agent/)).toBeInTheDocument();
  });

  it('keeps a workflow-definition failure event visible in the log console while another agent is active', async () => {
    vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(
      runWithStatus('RUNNING', {
        events: [
          {
            timestamp: new Date().toISOString(),
            agentId: 'requirement',
            title: 'Extracting requirements',
            status: 'RUNNING',
            evidenceRef: '',
          },
          {
            timestamp: new Date().toISOString(),
            agentId: 'workflow-definition',
            title: 'Workflow definition could not be read',
            status: 'FAILED',
            evidenceRef: '',
          },
        ],
      }),
    );

    await act(async () => {
      render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
    });

    await waitFor(
      () => expect(workflowApi.getWorkflowExecution).toHaveBeenCalledWith('wf-1', 'run-1'),
      { timeout: 3000 },
    );
    const logTextarea = await screen.findByLabelText<HTMLTextAreaElement>('Agent execution log');
    expect(logTextarea.value).toContain('Workflow definition could not be read');
    expect(logTextarea.readOnly).toBe(true);
  });

  it('announces a workflow-definition failure event via the status live region', async () => {
    vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(
      runWithStatus('RUNNING', {
        events: [
          {
            timestamp: new Date().toISOString(),
            agentId: 'requirement',
            title: 'Extracting requirements',
            status: 'RUNNING',
            evidenceRef: '',
          },
          {
            timestamp: new Date().toISOString(),
            agentId: 'workflow-definition',
            title: 'Workflow definition could not be read',
            status: 'FAILED',
            evidenceRef: '',
          },
        ],
      }),
    );

    await act(async () => {
      render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
    });

    await waitFor(
      () => expect(workflowApi.getWorkflowExecution).toHaveBeenCalledWith('wf-1', 'run-1'),
      { timeout: 3000 },
    );
    const announcement = screen.getByText('Workflow definition could not be read', {
      selector: 'p.workflow-waiting-announcement',
    });
    expect(announcement).toHaveAttribute('role', 'status');
    expect(announcement).toHaveAttribute('aria-live', 'polite');
  });

  it('calls onRunEnded once the run reaches a terminal status', async () => {
    vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(runWithStatus('COMPLETED'));
    const onRunEnded = vi.fn();

    await act(async () => {
      render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" onRunEnded={onRunEnded} />);
    });

    await waitFor(() => expect(onRunEnded).toHaveBeenCalledTimes(1), { timeout: 3000 });
  });

  it('stops the run when the Stop button is clicked', async () => {
    vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(runWithStatus('RUNNING'));
    vi.mocked(api.stopAgentRun).mockResolvedValue(undefined);

    await act(async () => {
      render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
    });

    await act(async () => {
      screen.getByText('◼ Stop').click();
    });

    await waitFor(() => expect(api.stopAgentRun).toHaveBeenCalledWith('run-1'));
  });

  describe('human approval gate', () => {
    const CONTEXT: ApprovalGateContext = {
      runId: 'run-1',
      workflowId: 'wf-1',
      placementStage: 'realisation',
      stageOutcome: 'PUBLISHED',
      changeSummary: 'Added an export button.',
      changedPaths: ['src/A.tsx'],
      changedFileCount: 1,
      omittedFileCount: 0,
      branchName: 'realisation/wf-1-abc',
      pullRequestUrl: 'https://git.example.com/org/repo/pull/1',
      branchCompareUrl: 'https://git.example.com/org/repo/compare',
      iteration: 1,
      maxFeedbackIterations: 3,
      feedbackSupported: true,
      furtherFeedbackAllowed: true,
      commentHistory: [],
      openedAt: new Date().toISOString(),
    };

    it('keeps polling and does not call onRunEnded while AWAITING_APPROVAL', async () => {
      vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(
        runWithStatus('AWAITING_APPROVAL'),
      );
      vi.mocked(workflowApi.getApprovalGateContext).mockResolvedValue(CONTEXT);
      const onRunEnded = vi.fn();

      vi.useFakeTimers();
      try {
        render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" onRunEnded={onRunEnded} />);
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
      } finally {
        vi.useRealTimers();
      }

      expect(onRunEnded).not.toHaveBeenCalled();
      expect(screen.getByText('Awaiting approval')).toBeInTheDocument();
    });

    it('fetches the approval context exactly once per transition into AWAITING_APPROVAL, including a re-gate', async () => {
      vi.mocked(workflowApi.getWorkflowExecution)
        .mockResolvedValueOnce(runWithStatus('AWAITING_APPROVAL'))
        .mockResolvedValueOnce(runWithStatus('AWAITING_APPROVAL'))
        .mockResolvedValueOnce(runWithStatus('RUNNING'))
        .mockResolvedValueOnce(runWithStatus('AWAITING_APPROVAL'));
      vi.mocked(workflowApi.getApprovalGateContext).mockResolvedValue(CONTEXT);

      vi.useFakeTimers();
      try {
        render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
      } finally {
        vi.useRealTimers();
      }

      expect(workflowApi.getApprovalGateContext).toHaveBeenCalledTimes(2);
    });

    it('hosts the approval decision modal and submits Accept with the current iteration', async () => {
      vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(
        runWithStatus('AWAITING_APPROVAL'),
      );
      vi.mocked(workflowApi.getApprovalGateContext).mockResolvedValue(CONTEXT);
      vi.mocked(workflowApi.submitApprovalDecision).mockResolvedValue({
        runId: 'run-1',
        status: 'RUNNING',
        iteration: 1,
        message: 'ok',
      });

      await act(async () => {
        render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
      });

      await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument(), {
        timeout: 3000,
      });
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: 'Accept' }));
      });

      await waitFor(() =>
        expect(workflowApi.submitApprovalDecision).toHaveBeenCalledWith('run-1', {
          decision: 'ACCEPT',
          iteration: 1,
          comment: undefined,
        }),
      );
    });

    it('stops polling and renders a terminal explanation with a restart affordance for RUN_STATE_LOST', async () => {
      vi.mocked(workflowApi.getWorkflowExecution).mockRejectedValue(
        Object.assign(new Error('Failed to load workflow execution: 404'), { status: 404 }),
      );
      const onRestartRequested = vi.fn();

      await act(async () => {
        render(
          <WorkflowExecution
            workflow={WORKFLOW}
            runId="run-1"
            onRestartRequested={onRestartRequested}
          />,
        );
      });

      await waitFor(
        () => expect(screen.getByRole('alert')).toHaveTextContent(/state of this run was lost/i),
        { timeout: 3000 },
      );
      const callsAtTerminal = vi.mocked(workflowApi.getWorkflowExecution).mock.calls.length;

      vi.useFakeTimers();
      try {
        await act(async () => {
          await vi.advanceTimersByTimeAsync(3000);
        });
      } finally {
        vi.useRealTimers();
      }
      expect(vi.mocked(workflowApi.getWorkflowExecution).mock.calls.length).toBe(callsAtTerminal);

      fireEvent.click(screen.getByText('Start again'));
      expect(onRestartRequested).toHaveBeenCalledTimes(1);
    });

    it('renders a DENIED status chip with a label distinct from FAILED and CANCELLED', async () => {
      vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(runWithStatus('DENIED'));

      await act(async () => {
        render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
      });

      await waitFor(() => expect(screen.getByText('DENIED')).toBeInTheDocument(), {
        timeout: 3000,
      });
      expect(screen.queryByText('FAILED')).not.toBeInTheDocument();
      expect(screen.queryByText('CANCELLED')).not.toBeInTheDocument();
    });

    it('renders the branch artifact as plain text and the pull-request artifact as a link with rel="noopener noreferrer"', async () => {
      vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(
        runWithStatus('RUNNING', {
          generatedArtifacts: [
            'git-branch:realisation/wf-1-abc',
            'pull-request:https://git.example.com/org/repo/pull/7',
          ],
        }),
      );

      await act(async () => {
        render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
      });

      const branchLabel = await screen.findByText(
        'Branch: realisation/wf-1-abc',
        {},
        { timeout: 3000 },
      );
      expect(branchLabel.closest('a')).toBeNull();
      expect(screen.queryByRole('link', { name: /branch/i })).not.toBeInTheDocument();

      const prLink = screen.getByText('Pull request');
      expect(prLink.closest('a')).toHaveAttribute(
        'href',
        'https://git.example.com/org/repo/pull/7',
      );
      expect(prLink.closest('a')).toHaveAttribute('rel', 'noopener noreferrer');
    });
  });

  describe('execution history integration', () => {
    it('still immediately renders the live detail for a run passed in via the runId prop (regression: just-started flow unchanged)', async () => {
      vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(runWithStatus('RUNNING'));

      await act(async () => {
        render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
      });

      expect(screen.getByText('◼ Stop')).toBeInTheDocument();
      expect(screen.getByText('RUNNING')).toBeInTheDocument();
      expect(await screen.findByText('Requirement Agent')).toBeInTheDocument();
    });

    it('shows full live controls (Stop button, auto-opening approval modal) for a running execution opened from the history list, not via "just started"', async () => {
      vi.mocked(workflowApi.listWorkflowExecutions).mockResolvedValue({
        items: [
          {
            runId: 'run-9',
            workflowId: 'wf-1',
            status: 'RUNNING',
            startedAt: new Date().toISOString(),
            completedAt: null,
            durationMillis: null,
            startedBy: 'alice',
          },
        ],
        limit: 20,
        offset: 0,
        total: 1,
        hasMore: false,
      });
      vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(
        runWithStatus('AWAITING_APPROVAL', { runId: 'run-9' }),
      );
      vi.mocked(workflowApi.getApprovalGateContext).mockResolvedValue({
        runId: 'run-9',
        workflowId: 'wf-1',
        placementStage: 'realisation',
        stageOutcome: 'PUBLISHED',
        changeSummary: 'Added an export button.',
        changedPaths: ['src/A.tsx'],
        changedFileCount: 1,
        omittedFileCount: 0,
        branchName: 'realisation/wf-1-abc',
        pullRequestUrl: 'https://git.example.com/org/repo/pull/1',
        branchCompareUrl: 'https://git.example.com/org/repo/compare',
        iteration: 1,
        maxFeedbackIterations: 3,
        feedbackSupported: true,
        furtherFeedbackAllowed: true,
        commentHistory: [],
        openedAt: new Date().toISOString(),
      });

      await act(async () => {
        render(<WorkflowExecution workflow={WORKFLOW} runId={null} />);
      });

      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: 'run-9' }));
      });

      await waitFor(() =>
        expect(workflowApi.getWorkflowExecution).toHaveBeenCalledWith('wf-1', 'run-9'),
      );
      await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());
      expect(screen.getByText('◼ Stop')).toBeInTheDocument();
    });

    it('surfaces the approval UI when an already-AWAITING_APPROVAL row is selected from the history list', async () => {
      vi.mocked(workflowApi.listWorkflowExecutions).mockResolvedValue({
        items: [
          {
            runId: 'run-99',
            workflowId: 'wf-1',
            status: 'AWAITING_APPROVAL',
            startedAt: new Date().toISOString(),
            completedAt: null,
            durationMillis: null,
            startedBy: 'alice',
          },
        ],
        limit: 20,
        offset: 0,
        total: 1,
        hasMore: false,
      });
      vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(
        runWithStatus('AWAITING_APPROVAL', { runId: 'run-99' }),
      );
      vi.mocked(workflowApi.getApprovalGateContext).mockResolvedValue({
        runId: 'run-99',
        workflowId: 'wf-1',
        placementStage: 'realisation',
        stageOutcome: 'PUBLISHED',
        changeSummary: 'Added an export button.',
        changedPaths: ['src/A.tsx'],
        changedFileCount: 1,
        omittedFileCount: 0,
        branchName: 'realisation/wf-1-abc',
        pullRequestUrl: 'https://git.example.com/org/repo/pull/1',
        branchCompareUrl: 'https://git.example.com/org/repo/compare',
        iteration: 1,
        maxFeedbackIterations: 3,
        feedbackSupported: true,
        furtherFeedbackAllowed: true,
        commentHistory: [],
        openedAt: new Date().toISOString(),
      });

      await act(async () => {
        render(<WorkflowExecution workflow={WORKFLOW} runId={null} />);
      });

      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: 'run-99' }));
      });

      await waitFor(() => expect(workflowApi.getApprovalGateContext).toHaveBeenCalled());
      await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());
    });

    it('updates a running history row in place when it transitions to a terminal status on the next refresh, without duplicating or removing it', async () => {
      const initialPage: WorkflowExecutionPage = {
        items: [
          {
            runId: 'run-1',
            workflowId: 'wf-1',
            status: 'RUNNING',
            startedAt: new Date('2024-01-01T00:00:00.000Z').toISOString(),
            completedAt: null,
            durationMillis: null,
            startedBy: 'alice',
          },
        ],
        limit: 20,
        offset: 0,
        total: 1,
        hasMore: false,
      };
      const refreshedPage: WorkflowExecutionPage = {
        ...initialPage,
        items: [
          {
            ...initialPage.items[0],
            status: 'COMPLETED',
            completedAt: new Date('2024-01-01T00:05:00.000Z').toISOString(),
            durationMillis: 300000,
          },
        ],
      };
      vi.mocked(workflowApi.listWorkflowExecutions)
        .mockResolvedValueOnce(initialPage)
        .mockResolvedValue(refreshedPage);
      vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(runWithStatus('RUNNING'));

      vi.useFakeTimers();
      try {
        render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
        await act(async () => {
          await vi.advanceTimersByTimeAsync(0);
        });
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
      } finally {
        vi.useRealTimers();
      }

      const runButtons = screen.getAllByRole('button', { name: 'run-1' });
      expect(runButtons.length).toBe(1);
    });

    it('shows the history error state (never an empty list) when the history fetch fails', async () => {
      vi.mocked(workflowApi.listWorkflowExecutions).mockRejectedValue(new Error('boom'));

      await act(async () => {
        render(<WorkflowExecution workflow={WORKFLOW} runId={null} />);
      });

      expect(screen.getByRole('alert')).toBeInTheDocument();
      expect(
        screen.queryByText('This workflow has not been executed yet.'),
      ).not.toBeInTheDocument();
    });

    it('uses exactly one polling interval: detail is fetched on every tick and history is refreshed only on every second tick while a row is live, with no extra calls once everything is terminal', async () => {
      // A DIFFERENT runId ('run-42') than the selected/live run ('run-1') is used for the
      // history row here deliberately: this isolates the periodic history-refresh cadence
      // from the in-place merge that already happens on every detail poll (mergeHistoryItem
      // only touches a history row whose runId matches the currently-selected run).
      const runningHistoryPage = {
        items: [
          {
            runId: 'run-42',
            workflowId: 'wf-1',
            status: 'RUNNING',
            startedAt: new Date().toISOString(),
            completedAt: null,
            durationMillis: null,
            startedBy: null,
          },
        ],
        limit: 20,
        offset: 0,
        total: 1,
        hasMore: false,
      };
      const completedHistoryPage = {
        ...runningHistoryPage,
        items: [
          {
            ...runningHistoryPage.items[0],
            status: 'COMPLETED',
            completedAt: new Date().toISOString(),
            durationMillis: 1000,
          },
        ],
      };
      vi.mocked(workflowApi.listWorkflowExecutions)
        .mockResolvedValueOnce(runningHistoryPage)
        .mockResolvedValue(completedHistoryPage);
      vi.mocked(workflowApi.getWorkflowExecution)
        .mockResolvedValueOnce(runWithStatus('RUNNING'))
        .mockResolvedValue(runWithStatus('COMPLETED'));

      vi.useFakeTimers();
      try {
        render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
        await act(async () => {
          await vi.advanceTimersByTimeAsync(0);
        });
        const historyCallsAfterMount = vi.mocked(workflowApi.listWorkflowExecutions).mock.calls
          .length;
        const detailCallsAfterMount = vi.mocked(workflowApi.getWorkflowExecution).mock.calls.length;

        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });

        const detailCallsAfterTicks = vi.mocked(workflowApi.getWorkflowExecution).mock.calls.length;
        expect(detailCallsAfterTicks).toBeGreaterThan(detailCallsAfterMount);

        const historyCallsBeforeSettling = vi.mocked(workflowApi.listWorkflowExecutions).mock.calls
          .length;
        expect(historyCallsBeforeSettling).toBeGreaterThan(historyCallsAfterMount);

        const detailCallsAtSettling = vi.mocked(workflowApi.getWorkflowExecution).mock.calls.length;

        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });

        expect(vi.mocked(workflowApi.getWorkflowExecution).mock.calls.length).toBe(
          detailCallsAtSettling,
        );
        expect(vi.mocked(workflowApi.listWorkflowExecutions).mock.calls.length).toBe(
          historyCallsBeforeSettling,
        );
      } finally {
        vi.useRealTimers();
      }
    });

    it('preserves rows loaded via "Load more" through a subsequent background refresh (no truncation, duplication, or reordering)', async () => {
      const makeRow = (n: number, status = 'COMPLETED') => ({
        runId: `run-${n}`,
        workflowId: 'wf-1',
        status,
        startedAt: new Date(2024, 0, n).toISOString(),
        completedAt: status === 'COMPLETED' ? new Date(2024, 0, n, 1).toISOString() : null,
        durationMillis: status === 'COMPLETED' ? 3600000 : null,
        startedBy: null,
      });
      const firstPage: WorkflowExecutionPage = {
        items: [makeRow(1, 'RUNNING'), ...Array.from({ length: 19 }, (_, i) => makeRow(i + 2))],
        limit: 20,
        offset: 0,
        total: 40,
        hasMore: true,
      };
      const secondPage: WorkflowExecutionPage = {
        items: Array.from({ length: 20 }, (_, i) => makeRow(i + 21)),
        limit: 20,
        offset: 20,
        total: 40,
        hasMore: false,
      };
      const refreshedWindow: WorkflowExecutionPage = {
        items: [makeRow(1, 'COMPLETED'), ...Array.from({ length: 39 }, (_, i) => makeRow(i + 2))],
        limit: 40,
        offset: 0,
        total: 40,
        hasMore: false,
      };
      vi.mocked(workflowApi.listWorkflowExecutions)
        .mockResolvedValueOnce(firstPage)
        .mockResolvedValueOnce(secondPage)
        .mockResolvedValue(refreshedWindow);

      vi.useFakeTimers();
      try {
        render(<WorkflowExecution workflow={WORKFLOW} runId={null} />);
        await act(async () => {
          await vi.advanceTimersByTimeAsync(0);
        });

        await act(async () => {
          fireEvent.click(screen.getByRole('button', { name: 'Load more' }));
        });
        await act(async () => {
          await vi.advanceTimersByTimeAsync(0);
        });

        let rows = screen.getAllByRole('row').slice(1); // drop header row
        expect(rows.length).toBe(40);

        expect(workflowApi.listWorkflowExecutions).toHaveBeenLastCalledWith('wf-1', {
          limit: 20,
          offset: 20,
        });

        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });

        expect(workflowApi.listWorkflowExecutions).toHaveBeenLastCalledWith('wf-1', {
          limit: 40,
          offset: 0,
        });

        rows = screen.getAllByRole('row').slice(1);
        expect(rows.length).toBe(40);
        const runIds = rows.map((row) => row.querySelector('button')?.textContent);
        const uniqueRunIds = new Set(runIds);
        expect(uniqueRunIds.size).toBe(40);
        expect(runIds[0]).toBe('run-1');
        expect(runIds[39]).toBe('run-40');
      } finally {
        vi.useRealTimers();
      }
    });

    it('refreshes the history list on the expected tick cadence even when no run is selected, transitioning a running row to terminal in place', async () => {
      const runningPage: WorkflowExecutionPage = {
        items: [
          {
            runId: 'run-77',
            workflowId: 'wf-1',
            status: 'RUNNING',
            startedAt: new Date().toISOString(),
            completedAt: null,
            durationMillis: null,
            startedBy: null,
          },
        ],
        limit: 20,
        offset: 0,
        total: 1,
        hasMore: false,
      };
      const completedPage: WorkflowExecutionPage = {
        ...runningPage,
        items: [
          {
            ...runningPage.items[0],
            status: 'COMPLETED',
            completedAt: new Date().toISOString(),
            durationMillis: 1000,
          },
        ],
      };
      vi.mocked(workflowApi.listWorkflowExecutions)
        .mockResolvedValueOnce(runningPage)
        .mockResolvedValue(completedPage);

      vi.useFakeTimers();
      try {
        render(<WorkflowExecution workflow={WORKFLOW} runId={null} />);
        await act(async () => {
          await vi.advanceTimersByTimeAsync(0);
        });
        expect(screen.getByRole('button', { name: 'run-77' })).toBeInTheDocument();
        expect(workflowApi.getWorkflowExecution).not.toHaveBeenCalled();

        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });

        expect(workflowApi.getWorkflowExecution).not.toHaveBeenCalled();
        const row = screen.getByRole('button', { name: 'run-77' }).closest('tr')!;
        expect(row).toHaveTextContent('COMPLETED');
      } finally {
        vi.useRealTimers();
      }
    });

    it('never renders a fabricated RUNNING status when a COMPLETED run is opened from the history list', async () => {
      vi.mocked(workflowApi.listWorkflowExecutions).mockResolvedValue({
        items: [
          {
            runId: 'run-88',
            workflowId: 'wf-1',
            status: 'COMPLETED',
            startedAt: new Date().toISOString(),
            completedAt: new Date().toISOString(),
            durationMillis: 1000,
            startedBy: 'alice',
          },
        ],
        limit: 20,
        offset: 0,
        total: 1,
        hasMore: false,
      });
      let resolveDetail!: (run: AgentRun) => void;
      vi.mocked(workflowApi.getWorkflowExecution).mockReturnValue(
        new Promise((resolve) => {
          resolveDetail = resolve;
        }),
      );

      await act(async () => {
        render(<WorkflowExecution workflow={WORKFLOW} runId={null} />);
      });

      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: 'run-88' }));
      });

      expect(screen.queryByText('RUNNING')).not.toBeInTheDocument();
      expect(screen.getAllByText('COMPLETED').length).toBeGreaterThan(0);

      await act(async () => {
        resolveDetail(runWithStatus('COMPLETED', { runId: 'run-88' }));
      });

      expect(screen.queryByText('RUNNING')).not.toBeInTheDocument();
    });

    it('does not fetch or render execution history when showHistory is false', async () => {
      vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(runWithStatus('RUNNING'));

      await act(async () => {
        render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" showHistory={false} />);
      });

      expect(workflowApi.listWorkflowExecutions).not.toHaveBeenCalled();
      expect(screen.queryByRole('table')).not.toBeInTheDocument();
      expect(screen.getByText('◼ Stop')).toBeInTheDocument();
      expect(screen.getByText('RUNNING')).toBeInTheDocument();
      expect(await screen.findByText('Requirement Agent')).toBeInTheDocument();
    });
  });

  describe('workflow orb chain status', () => {
    it('keeps polling and does not call onRunEnded while AWAITING_CHILD_WORKFLOW', async () => {
      vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(
        runWithStatus('AWAITING_CHILD_WORKFLOW'),
      );
      const onRunEnded = vi.fn();

      vi.useFakeTimers();
      try {
        render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" onRunEnded={onRunEnded} />);
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
      } finally {
        vi.useRealTimers();
      }

      expect(onRunEnded).not.toHaveBeenCalled();
      expect(screen.getByText('Waiting for child workflow')).toBeInTheDocument();
    });

    it('stops polling and calls onRunEnded when the run reaches TIMED_OUT', async () => {
      vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(runWithStatus('TIMED_OUT'));
      const onRunEnded = vi.fn();

      await act(async () => {
        render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" onRunEnded={onRunEnded} />);
      });

      await waitFor(() => expect(onRunEnded).toHaveBeenCalledTimes(1), { timeout: 3000 });
      expect(screen.getByText('Timed out')).toBeInTheDocument();
    });

    it('stops polling and calls onRunEnded when the run reaches BLOCKED', async () => {
      vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(runWithStatus('BLOCKED'));
      const onRunEnded = vi.fn();

      await act(async () => {
        render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" onRunEnded={onRunEnded} />);
      });

      await waitFor(() => expect(onRunEnded).toHaveBeenCalledTimes(1), { timeout: 3000 });
      expect(screen.getByText('Blocked')).toBeInTheDocument();
    });

    it('renders the latest workflow-orb event as plain selectable text with the child run id', async () => {
      vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(
        runWithStatus('RUNNING', {
          events: [
            {
              timestamp: new Date().toISOString(),
              agentId: 'workflow-orb',
              title: "Workflow 'Spec creation' started (SEQUENTIAL, run child-run-42)",
              status: 'RUNNING',
              evidenceRef: 'run://child-run-42',
            },
          ],
        }),
      );

      await act(async () => {
        render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
      });

      await waitFor(
        () => expect(workflowApi.getWorkflowExecution).toHaveBeenCalledWith('wf-1', 'run-1'),
        { timeout: 3000 },
      );
      const childRunIdEl = await screen.findByText('child-run-42');
      expect(childRunIdEl.tagName).toBe('CODE');
    });

    it('announces only the latest waiting-for-child transition, not an accumulation of every poll tick', async () => {
      vi.mocked(workflowApi.getWorkflowExecution)
        .mockResolvedValueOnce(runWithStatus('RUNNING'))
        .mockResolvedValueOnce(runWithStatus('AWAITING_CHILD_WORKFLOW'))
        .mockResolvedValueOnce(runWithStatus('AWAITING_CHILD_WORKFLOW'))
        .mockResolvedValueOnce(runWithStatus('COMPLETED'));

      vi.useFakeTimers();
      try {
        render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
      } finally {
        vi.useRealTimers();
      }

      expect(screen.getByText(/No longer waiting/i)).toBeInTheDocument();
      expect(screen.queryAllByText(/Waiting for a triggered child workflow/i)).toHaveLength(0);
    });

    it('renders a workflow orb from workflow.workflowOrbs at its configured anchor, with a Running status derived from the run event stream', async () => {
      const workflowWithOrb: WorkflowDefinition = {
        ...WORKFLOW,
        workflowOrbs: [
          { workflowId: 'wf-child', mode: 'SEQUENTIAL', placementStage: 'requirement' },
        ],
      };

      vi.mocked(workflowApi.listWorkflows).mockResolvedValue([
        { ...WORKFLOW, id: 'wf-child', name: 'Spec Creation Child' },
      ]);

      vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(
        runWithStatus('AWAITING_CHILD_WORKFLOW', {
          events: [
            {
              timestamp: 't1',
              agentId: 'requirement',
              title: 'x',
              status: 'RUNNING',
              evidenceRef: '',
            },
            {
              timestamp: 't2',
              agentId: 'workflow-orb',
              title: 'Started child workflow Spec Creation Child (SEQUENTIAL)',
              status: 'AWAITING_CHILD_WORKFLOW',
              evidenceRef: 'run://child-run-1',
            },
          ],
        }),
      );

      let container: HTMLElement;
      await act(async () => {
        container = render(
          <WorkflowExecution workflow={workflowWithOrb} runId="run-1" />,
        ).container;
      });

      await waitFor(
        () => {
          const orbEl = screen.getByLabelText('Workflow orb: Spec Creation Child, sequential');
          expect(orbEl.textContent).toContain('Running');
          const names = Array.from(container.querySelectorAll('.sequencer-meta strong')).map(
            (el) => el.textContent,
          );
          expect(names).toEqual(['Requirement Agent', 'Spec Creation Child', 'Evidence Agent']);
        },
        { timeout: 3000 },
      );
    });

    it('renders an orb with an unknown (non-blank) placementStage last, with a visible anchor-removed indicator, distinct from a blank-placementStage orb', async () => {
      const workflowWithOrphanedOrb: WorkflowDefinition = {
        ...WORKFLOW,
        workflowOrbs: [
          { workflowId: 'wf-child', mode: 'SEQUENTIAL', placementStage: 'implementation' },
        ],
      };

      vi.mocked(workflowApi.listWorkflows).mockResolvedValue([
        { ...WORKFLOW, id: 'wf-child', name: 'Spec Creation Child' },
      ]);

      vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(runWithStatus('RUNNING'));

      let container: HTMLElement;
      await act(async () => {
        container = render(
          <WorkflowExecution workflow={workflowWithOrphanedOrb} runId="run-1" />,
        ).container;
      });

      await waitFor(
        () => {
          const orbEl = screen.getByLabelText('Workflow orb: Spec Creation Child, sequential');
          expect(orbEl.textContent).toContain('anchor stage removed');
          const names = Array.from(container.querySelectorAll('.sequencer-meta strong')).map(
            (el) => el.textContent,
          );
          expect(names).toEqual(['Requirement Agent', 'Evidence Agent', 'Spec Creation Child']);
        },
        { timeout: 3000 },
      );
    });
  });

  it('renders a FAILED run failureSummary inside an aria-live polite status element', async () => {
    vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(
      runWithStatus('FAILED', { failureSummary: 'the run failed because X' }),
    );

    await act(async () => {
      render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
    });

    await waitFor(() => {
      expect(screen.getByText('the run failed because X')).toBeInTheDocument();
    });
    const el = screen.getByText('the run failed because X');
    expect(el).toHaveAttribute('aria-live', 'polite');
    expect(el).toHaveAttribute('role', 'status');
  });

  it('renders no failure summary text for a COMPLETED run', async () => {
    vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(runWithStatus('COMPLETED'));

    await act(async () => {
      render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
    });

    await waitFor(() => {
      expect(screen.getByText('COMPLETED')).toBeInTheDocument();
    });
    expect(screen.queryByText(/the run failed/)).not.toBeInTheDocument();
  });

  it('renders no failure summary text for a FAILED run whose failureSummary is null', async () => {
    vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(
      runWithStatus('FAILED', { failureSummary: null }),
    );

    await act(async () => {
      render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
    });

    await waitFor(() => {
      expect(screen.getByText('FAILED')).toBeInTheDocument();
    });
    expect(screen.queryByText(/the run failed/)).not.toBeInTheDocument();
  });

  it('clears the previously-shown failure summary when the selected run changes', async () => {
    vi.mocked(workflowApi.getWorkflowExecution).mockImplementation(
      async (_workflowId: string, currentRunId: string) => {
        if (currentRunId === 'run-1') {
          return runWithStatus('FAILED', { failureSummary: 'run one failed' });
        }
        return runWithStatus('RUNNING', { runId: 'run-2' });
      },
    );

    let rerender!: ReturnType<typeof render>['rerender'];
    await act(async () => {
      const result = render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
      rerender = result.rerender;
    });

    await waitFor(() => {
      expect(screen.getByText('run one failed')).toBeInTheDocument();
    });

    await act(async () => {
      rerender(<WorkflowExecution workflow={WORKFLOW} runId="run-2" />);
    });

    await waitFor(() => {
      expect(screen.queryByText('run one failed')).not.toBeInTheDocument();
    });
  });

  it('renders a failureSummary containing script- and URL-like text as inert literal text', async () => {
    const dangerous = 'error at <script>alert(1)</script> see https://example.com/evil for details';
    vi.mocked(workflowApi.getWorkflowExecution).mockResolvedValue(
      runWithStatus('FAILED', { failureSummary: dangerous }),
    );

    await act(async () => {
      render(<WorkflowExecution workflow={WORKFLOW} runId="run-1" />);
    });

    await waitFor(() => {
      expect(screen.getByText(dangerous)).toBeInTheDocument();
    });
    const el = screen.getByText(dangerous);
    expect(el.textContent).toBe(dangerous);
    expect(document.querySelector('a')).toBeNull();
    expect(document.querySelector('script')).toBeNull();
  });
});
