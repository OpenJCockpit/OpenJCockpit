import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@openjcockpit/api';
import type { AgentRun, WorkflowDefinition, WorkflowExecutionPage } from '@openjcockpit/api';
import { useRunHistory } from './useRunHistory';

vi.mock('@openjcockpit/api', async (importOriginal) =>
  (await import('../testUtils/mockApi')).automockApiClients(await importOriginal()),
);

const WORKFLOW = { id: 'wf-1', name: 'WF' } as WorkflowDefinition;

type Item = WorkflowExecutionPage['items'][number];

function item(runId: string, overrides: Partial<Item> = {}): Item {
  return {
    runId,
    status: 'RUNNING',
    startedAt: '2026-01-01T00:00:00Z',
    completedAt: null,
    durationMillis: null,
    startedBy: null,
    ...overrides,
  } as Item;
}

function page(
  items: Item[],
  overrides: Partial<WorkflowExecutionPage> = {},
): WorkflowExecutionPage {
  return { items, limit: 20, offset: 0, total: items.length, hasMore: false, ...overrides };
}

function run(runId: string, overrides: Partial<AgentRun> = {}): AgentRun {
  return {
    runId,
    status: 'COMPLETED',
    workflowId: 'wf-1',
    events: [],
    generatedArtifacts: [],
    completedAt: '2026-01-01T00:00:10Z',
    ...overrides,
  } as AgentRun;
}

describe('useRunHistory', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('clears history and does not fetch when there is no workflow', () => {
    const { result } = renderHook(() => useRunHistory(null, true));
    expect(result.current.historyPage).toBeNull();
    expect(api.listWorkflowExecutions).not.toHaveBeenCalled();
  });

  it('does not fetch on mount when history is hidden', () => {
    renderHook(() => useRunHistory(WORKFLOW, false));
    expect(api.listWorkflowExecutions).not.toHaveBeenCalled();
  });

  it('reports a generic message when the initial fetch rejects with a non-Error', async () => {
    vi.mocked(api.listWorkflowExecutions).mockRejectedValue('nope');
    const { result } = renderHook(() => useRunHistory(WORKFLOW, true));
    await waitFor(() =>
      expect(result.current.historyError).toBe('Failed to load execution history'),
    );
  });

  describe('mergeHistoryItem', () => {
    async function loaded(items: Item[]) {
      vi.mocked(api.listWorkflowExecutions).mockResolvedValue(page(items));
      const hook = renderHook(() => useRunHistory(WORKFLOW, true));
      await waitFor(() => expect(hook.result.current.historyPage).not.toBeNull());
      return hook;
    }

    it('is a no-op before any page is loaded', () => {
      const { result } = renderHook(() => useRunHistory(WORKFLOW, false));
      act(() => result.current.mergeHistoryItem(run('r1')));
      expect(result.current.historyPage).toBeNull();
    });

    it('ignores runs without a workflow id', async () => {
      const { result } = await loaded([item('r1')]);
      act(() => result.current.mergeHistoryItem(run('r1', { workflowId: undefined })));
      expect(result.current.historyPage?.items[0]?.status).toBe('RUNNING');
    });

    it('ignores runs that are not part of the loaded page', async () => {
      const { result } = await loaded([item('r1')]);
      const before = result.current.historyPage;
      act(() => result.current.mergeHistoryItem(run('other')));
      expect(result.current.historyPage).toBe(before);
    });

    it('updates status, completion and duration of a loaded row', async () => {
      const { result } = await loaded([item('r1'), item('r2')]);
      act(() => result.current.mergeHistoryItem(run('r1')));
      const [first, second] = result.current.historyPage?.items ?? [];
      expect(first).toMatchObject({ status: 'COMPLETED', durationMillis: 10000 });
      expect(second?.status).toBe('RUNNING');
    });

    it('keeps the previous duration when the run has no completion time', async () => {
      const { result } = await loaded([item('r1', { durationMillis: 5 })]);
      act(() => result.current.mergeHistoryItem(run('r1', { completedAt: undefined })));
      expect(result.current.historyPage?.items[0]).toMatchObject({
        completedAt: null,
        durationMillis: 5,
      });
    });
  });

  describe('refreshHistoryWindow', () => {
    it('adopts the fetched page when nothing was loaded yet', async () => {
      vi.mocked(api.listWorkflowExecutions).mockResolvedValue(page([item('r1')]));
      const { result } = renderHook(() => useRunHistory(WORKFLOW, false));
      await act(async () => result.current.refreshHistoryWindow('wf-1'));
      expect(api.listWorkflowExecutions).toHaveBeenCalledWith('wf-1', { limit: 20, offset: 0 });
      expect(result.current.historyPage?.items).toHaveLength(1);
    });

    it('merges existing rows in place and appends new ones', async () => {
      vi.mocked(api.listWorkflowExecutions).mockResolvedValueOnce(page([item('r1'), item('r2')]));
      const { result } = renderHook(() => useRunHistory(WORKFLOW, true));
      await waitFor(() => expect(result.current.historyPage).not.toBeNull());

      vi.mocked(api.listWorkflowExecutions).mockResolvedValueOnce(
        page([item('r3'), item('r1', { status: 'COMPLETED' })], { total: 3 }),
      );
      await act(async () => result.current.refreshHistoryWindow('wf-1'));

      expect(result.current.historyPage?.items.map((i) => [i.runId, i.status])).toEqual([
        ['r1', 'COMPLETED'],
        ['r2', 'RUNNING'],
        ['r3', 'RUNNING'],
      ]);
    });

    it('swallows a transient failure and keeps the loaded history', async () => {
      vi.mocked(api.listWorkflowExecutions).mockResolvedValueOnce(page([item('r1')]));
      const { result } = renderHook(() => useRunHistory(WORKFLOW, true));
      await waitFor(() => expect(result.current.historyPage).not.toBeNull());

      vi.mocked(api.listWorkflowExecutions).mockRejectedValueOnce(new Error('down'));
      await act(async () => result.current.refreshHistoryWindow('wf-1'));

      expect(result.current.historyPage?.items).toHaveLength(1);
      expect(result.current.historyError).toBeNull();
    });
  });

  describe('loadMore', () => {
    it('does nothing without a workflow', async () => {
      const { result } = renderHook(() => useRunHistory(null, true));
      await act(async () => result.current.loadMore());
      expect(api.listWorkflowExecutions).not.toHaveBeenCalled();
    });

    it('appends the next page after the loaded rows', async () => {
      vi.mocked(api.listWorkflowExecutions).mockResolvedValueOnce(
        page([item('r1')], { hasMore: true, total: 2 }),
      );
      const { result } = renderHook(() => useRunHistory(WORKFLOW, true));
      await waitFor(() => expect(result.current.historyPage).not.toBeNull());

      vi.mocked(api.listWorkflowExecutions).mockResolvedValueOnce(
        page([item('r2')], { offset: 1, total: 2 }),
      );
      await act(async () => result.current.loadMore());

      expect(api.listWorkflowExecutions).toHaveBeenLastCalledWith('wf-1', { limit: 20, offset: 1 });
      expect(result.current.historyPage?.items.map((i) => i.runId)).toEqual(['r1', 'r2']);
    });

    it('uses the fetched page as is when nothing was loaded before', async () => {
      vi.mocked(api.listWorkflowExecutions).mockResolvedValue(page([item('r9')]));
      const { result } = renderHook(() => useRunHistory(WORKFLOW, false));
      await act(async () => result.current.loadMore());
      expect(api.listWorkflowExecutions).toHaveBeenCalledWith('wf-1', { limit: 20, offset: 0 });
      expect(result.current.historyPage?.items.map((i) => i.runId)).toEqual(['r9']);
    });

    it('surfaces an Error message, or a generic one for non-Error rejections', async () => {
      const { result } = renderHook(() => useRunHistory(WORKFLOW, false));

      vi.mocked(api.listWorkflowExecutions).mockRejectedValueOnce(new Error('page failed'));
      await act(async () => result.current.loadMore());
      expect(result.current.historyError).toBe('page failed');

      vi.mocked(api.listWorkflowExecutions).mockRejectedValueOnce('nope');
      await act(async () => result.current.loadMore());
      expect(result.current.historyError).toBe('Failed to load execution history');
    });
  });
});
