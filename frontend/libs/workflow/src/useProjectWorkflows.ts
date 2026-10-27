import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  listWorkflowGroups,
  listWorkflows,
  queryKeys,
  workflowsForProject,
} from '@openjcockpit/api';
import type { WorkflowDefinition } from '@openjcockpit/api';

const NO_WORKFLOWS: WorkflowDefinition[] = [];

/** Workflows visible to the project (its own plus global ones). Failed loads yield an empty list. */
export function useProjectWorkflows(projectName: string | undefined, enabled = true) {
  const workflows = useQuery({
    queryKey: queryKeys.workflows,
    queryFn: ({ signal }) => listWorkflows(signal),
    enabled,
  });
  const groups = useQuery({
    queryKey: queryKeys.workflowGroups,
    queryFn: ({ signal }) => listWorkflowGroups(signal),
    enabled,
  });
  return useMemo(
    () => workflowsForProject(workflows.data ?? NO_WORKFLOWS, groups.data ?? [], projectName),
    [workflows.data, groups.data, projectName],
  );
}
