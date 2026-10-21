import { useQuery } from '@tanstack/react-query';
import { getSpecInitStatus, loadProjectSpecs, loadWorkspace } from '@openjcockpit/api';
import type { Project } from '@openjcockpit/api';

export function useWorkspaceQuery(project: Project) {
  const customerId = project.customerId ?? project.id;
  return useQuery({
    queryKey: ['workspace', customerId],
    queryFn: ({ signal }) => loadWorkspace(customerId, signal),
  });
}

/**
 * Spec files of the project. Without specs, a spec-init branch (pull request) may already be
 * open; its status is fetched in the same query so the panel can link to it.
 */
export function useProjectSpecsQuery(projectId: string) {
  return useQuery({
    queryKey: ['project-specs', projectId],
    queryFn: async ({ signal }) => {
      const specs = await loadProjectSpecs(projectId, signal);
      const specInitStatus = specs.length === 0 ? await getSpecInitStatus(projectId, signal) : null;
      return { specs, specInitStatus };
    },
  });
}
