import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { listWorkflows, queryKeys } from '@openjcockpit/api';
import type { WorkflowDefinition } from '@openjcockpit/api';

/** The workflow list query, on the cache entry the workflow page reads. */
export function useWorkflowsQuery(): UseQueryResult<WorkflowDefinition[]> {
  return useQuery({
    queryKey: queryKeys.workflows,
    queryFn: ({ signal }) => listWorkflows(signal),
  });
}

/** The workflow list, or undefined until loaded. */
export function useWorkflows(): WorkflowDefinition[] | undefined {
  return useWorkflowsQuery().data;
}

/** Rail badge of the workflow app: how many workflows exist, or null while unknown or none. */
export function useWorkflowCount(): number | null {
  const workflows = useWorkflows();
  return workflows && workflows.length > 0 ? workflows.length : null;
}
