import { useCallback, useRef } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getSpecQueue, queryKeys } from '@openjcockpit/api';
import type { SpecQueue } from '@openjcockpit/api';

const POLL_INTERVAL_MS = 5000;
const MAX_POLL_INTERVAL_MS = 60000;

export function messageOf(e: unknown, fallback: string): string {
  return e instanceof Error && e.message ? e.message : fallback;
}

/** Polls a project's spec queue (with exponential backoff while loads fail) and writes responses back into the cache. */
export function useSpecQueue(projectId: string | null) {
  const queryClient = useQueryClient();
  const queueKey = queryKeys.specQueue(projectId);
  // react-query resets its own failure counters on every fetch, so count consecutive failures here.
  const failures = useRef({ projectId, count: 0 });
  const consecutiveFailures = () =>
    failures.current.projectId === projectId ? failures.current.count : 0;
  const queueQuery = useQuery({
    queryKey: queueKey,
    queryFn: async () => {
      try {
        const loaded = await getSpecQueue(projectId as string);
        failures.current = { projectId, count: 0 };
        return loaded;
      } catch (e) {
        failures.current = { projectId, count: consecutiveFailures() + 1 };
        throw e;
      }
    },
    enabled: projectId !== null,
    // Polling is the retry; back off exponentially while loads keep failing.
    retry: false,
    refetchInterval: () =>
      document.hidden
        ? false
        : Math.min(POLL_INTERVAL_MS * 2 ** consecutiveFailures(), MAX_POLL_INTERVAL_MS),
    refetchIntervalInBackground: false,
  });
  const queue = queueQuery.data ?? null;
  const loadError = queueQuery.isError
    ? messageOf(queueQuery.error, 'Failed to load the spec queue')
    : null;

  /** Writes a queue response; an in-flight poll is cancelled so it cannot overwrite it. */
  const applyQueue = useCallback(
    async (next: SpecQueue) => {
      await queryClient.cancelQueries({ queryKey: queryKeys.specQueue(projectId) });
      queryClient.setQueryData(queryKeys.specQueue(projectId), next);
    },
    [queryClient, projectId],
  );

  const refresh = useCallback(async () => {
    await queryClient.refetchQueries({ queryKey: queryKeys.specQueue(projectId) });
  }, [queryClient, projectId]);

  const applyAutoMergeAllowed = useCallback(
    async (autoMergeAllowed: boolean) => {
      await queryClient.cancelQueries({ queryKey: queryKeys.specQueue(projectId) });
      queryClient.setQueryData<SpecQueue>(queryKeys.specQueue(projectId), (q) =>
        q ? { ...q, autoMergeAllowed } : q,
      );
    },
    [queryClient, projectId],
  );

  return { queue, loadError, applyQueue, refresh, applyAutoMergeAllowed };
}
