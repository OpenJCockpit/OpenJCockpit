import { useQuery } from '@tanstack/react-query';
import { listWorkflows, queryKeys } from '@openjcockpit/api';
import type { WorkflowDefinition } from '@openjcockpit/api';

/** The workflow list, on the cache entry the workflow page reads. Undefined until loaded. */
export function useWorkflows(): WorkflowDefinition[] | undefined {
  const { data } = useQuery({
    queryKey: queryKeys.workflows,
    queryFn: ({ signal }) => listWorkflows(signal),
  });
  return data;
}

/** Rail badge of the workflow app: how many workflows exist, or null while unknown or none. */
export function useWorkflowCount(): number | null {
  const workflows = useWorkflows();
  return workflows && workflows.length > 0 ? workflows.length : null;
}
