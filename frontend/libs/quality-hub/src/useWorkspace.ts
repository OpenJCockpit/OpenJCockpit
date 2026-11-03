import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { loadWorkspace, queryKeys } from '@openjcockpit/api';
import type { Workspace } from '@openjcockpit/api';
import { useHubProject } from '@openjcockpit/hub-contract';

/** Workspace of the selected project, on the cache entry the dashboard reads. Disabled without a project. */
export function useWorkspace(): UseQueryResult<Workspace> & { hasProject: boolean } {
  const selected = useHubProject().selected;
  const customerId = selected ? (selected.customerId ?? selected.id) : null;
  const query = useQuery({
    queryKey: queryKeys.workspace(customerId ?? ''),
    queryFn: ({ signal }) => loadWorkspace(customerId as string, signal),
    enabled: customerId !== null,
  });
  return Object.assign(query, { hasProject: customerId !== null });
}
