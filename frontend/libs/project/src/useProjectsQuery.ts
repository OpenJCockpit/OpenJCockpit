import { useQuery } from '@tanstack/react-query';
import { loadProjects } from '@openjcockpit/api';

export const projectsQueryKey = ['projects'] as const;

/** Active projects for the signed-in user. Pass `enabled: false` until the auth token is set. */
export function useProjectsQuery(enabled = true) {
  return useQuery({
    queryKey: projectsQueryKey,
    queryFn: ({ signal }) => loadProjects(signal),
    enabled,
  });
}
