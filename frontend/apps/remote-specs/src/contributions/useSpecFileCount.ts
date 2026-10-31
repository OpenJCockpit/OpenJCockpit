import { useQuery } from '@tanstack/react-query';
import { loadProjectSpecs, queryKeys } from '@openjcockpit/api';
import { useHubProject } from '@openjcockpit/hub-contract';

/** Spec files of the selected project, on the cache entry the spec files page reads. Null if unknown. */
export function useSpecFileCount(): number | null {
  const projectId = useHubProject().selected?.id ?? null;
  const { data } = useQuery({
    queryKey: queryKeys.projectSpecFiles(projectId),
    queryFn: ({ signal }) => loadProjectSpecs(projectId as string, signal),
    enabled: projectId !== null,
  });
  return data ? data.length : null;
}

/** Rail badge of the spec files app: null (no badge) while unknown or when there are none. */
export function useSpecFileBadge(): number | null {
  const count = useSpecFileCount();
  return count !== null && count > 0 ? count : null;
}
