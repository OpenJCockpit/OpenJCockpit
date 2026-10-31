import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { getSpecQueue, queryKeys } from '@openjcockpit/api';
import type { SpecQueue } from '@openjcockpit/api';
import { useHubProject } from '@openjcockpit/hub-contract';

const REFRESH_MS = 30_000;

/**
 * The selected project's spec queue, on the cache entry the queue page uses, so the badge, the
 * widget, the top bar item and the page read one entry. Undefined without a project or before the
 * first load.
 */
export function useProjectQueueQuery(): UseQueryResult<SpecQueue> {
  const projectId = useHubProject().selected?.id ?? null;
  return useQuery({
    queryKey: queryKeys.specQueue(projectId),
    queryFn: () => getSpecQueue(projectId as string),
    enabled: projectId !== null,
    refetchInterval: REFRESH_MS,
  });
}

export function useProjectQueue(): SpecQueue | undefined {
  return useProjectQueueQuery().data;
}
