import { useEffect, useRef, useState } from 'react';
import type { AgentRun } from '../../types';
import type { WorkflowDefinition, WorkflowExecutionPage } from '../workflowTypes';
import { listWorkflowExecutions } from '../workflowApi';

const HISTORY_PAGE_SIZE = 20;
const HISTORY_PAGE_MAX = 100;

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

/** Owns the workflow's execution-history page and every way of loading/refreshing it. */
export function useRunHistory(workflow: WorkflowDefinition | null, showHistory: boolean) {
  const [historyPage, setHistoryPage] = useState<WorkflowExecutionPage | null>(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  // Lets the polling interval and per-run effects read the CURRENT page without being
  // torn down and recreated whenever it changes.
  const historyPageRef = useRef<WorkflowExecutionPage | null>(null);

  useEffect(() => {
    historyPageRef.current = historyPage;
  }, [historyPage]);

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

  async function loadMore() {
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

  return {
    historyPage,
    historyLoading,
    historyError,
    historyPageRef,
    fetchHistory,
    refreshHistoryWindow,
    mergeHistoryItem,
    loadMore,
  };
}
