import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@openjcockpit/api';
import type { AgentRun, WorkflowDefinition, WorkflowExecutionPage } from '@openjcockpit/api';
import { useRunEvents } from './useRunEvents';

vi.mock('@openjcockpit/api', async (importOriginal) =>
  (await import('../testUtils/mockApi')).automockApiClients(await importOriginal()),
);

const WORKFLOW = { id: 'wf-1', name: 'WF' } as WorkflowDefinition;

function run(runId: string, status = 'RUNNING'): AgentRun {
  return {
    runId,
    status,
    workflowId: 'wf-1',
    events: [],
    generatedArtifacts: [],
  } as unknown as AgentRun;
}

interface Deferred<T> {
  promise: Promise<T>;
  resolve: (value: T) => void;
  reject: (reason: unknown) => void;
}

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function setup(initial: {
  selectedRunId: string | null;
  showHistory?: boolean;
  page?: WorkflowExecutionPage | null;
}) {
  const mergeHistoryItem = vi.fn();
  const refreshHistoryWindow = vi.fn().mockResolvedValue(undefined);
  const historyPageRef = { current: initial.page ?? null };
  const hook = renderHook(
    ({ selectedRunId }: { selectedRunId: string | null }) =>
      useRunEvents({
        workflow: WORKFLOW,
        selectedRunId,
        showHistory: initial.showHistory ?? true,
        historyPageRef,
        mergeHistoryItem,
        refreshHistoryWindow,
      }),
    { initialProps: { selectedRunId: initial.selectedRunId } },
  );
  return { ...hook, mergeHistoryItem, refreshHistoryWindow };
}

describe('useRunEvents', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('ignores a detail response that arrives after the selection moved on', async () => {
    const stale = deferred<AgentRun>();
    vi.mocked(api.getWorkflowExecution).mockImplementation((_wf, id) =>
      id === 'a' ? stale.promise : Promise.resolve(run('b', 'COMPLETED')),
    );
    const { result, rerender } = setup({ selectedRunId: 'a' });

    rerender({ selectedRunId: 'b' });
    await act(async () => {});
    expect(result.current.runStatus).toBe('COMPLETED');

    await act(async () => {
      stale.resolve(run('a', 'FAILED'));
    });
    expect(result.current.runStatus).toBe('COMPLETED');
  });

  it('ignores a detail failure that arrives after the selection moved on', async () => {
    const stale = deferred<AgentRun>();
    vi.mocked(api.getWorkflowExecution).mockImplementation((_wf, id) =>
      id === 'a' ? stale.promise : Promise.resolve(run('b')),
    );
    const { result, rerender } = setup({ selectedRunId: 'a' });

    rerender({ selectedRunId: 'b' });
    await act(async () => {});
    await act(async () => {
      stale.reject(Object.assign(new Error('gone'), { status: 404 }));
    });

    expect(result.current.runStatus).toBe('RUNNING');
  });

  it('keeps the run status on a transient (non-404) polling error', async () => {
    vi.mocked(api.getWorkflowExecution).mockRejectedValue(new Error('flaky'));
    const { result } = setup({ selectedRunId: 'a' });
    await act(async () => {});
    expect(result.current.runStatus).toBe('RUNNING');
  });

  it('logs a warning and keeps going when the approval context cannot be loaded', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    vi.mocked(api.getWorkflowExecution).mockResolvedValue(run('a', 'AWAITING_APPROVAL'));
    vi.mocked(api.getApprovalGateContext).mockRejectedValue(new Error('ctx down'));
    const { result } = setup({ selectedRunId: 'a' });
    await act(async () => {});

    expect(warn).toHaveBeenCalledWith('Failed to load approval gate context', expect.any(Error));
    expect(result.current.approvalContext).toBeNull();
    warn.mockRestore();
  });

  it('does not refresh the history window on ticks when history is hidden', async () => {
    vi.useFakeTimers();
    vi.mocked(api.getWorkflowExecution).mockResolvedValue(run('a'));
    const { refreshHistoryWindow } = setup({ selectedRunId: 'a', showHistory: false });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    expect(refreshHistoryWindow).not.toHaveBeenCalled();
  });

  it('does not refresh the history window while no history page is loaded', async () => {
    vi.useFakeTimers();
    const { refreshHistoryWindow } = setup({ selectedRunId: null, page: null });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    expect(refreshHistoryWindow).not.toHaveBeenCalled();
  });
});
