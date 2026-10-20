import { useQuery } from '@tanstack/react-query';
import { listWorkflowGroups, listWorkflows } from '@openjcockpit/api';
import { fetchLinkableAgents } from '../../../linkableAgents';

export const workflowsQueryKey = ['workflows'] as const;
export const workflowGroupsQueryKey = ['workflow-groups'] as const;
export const linkableAgentsQueryKey = ['linkable-agents'] as const;

/** Workflows, groups and linkable agents; `loaded` once every query has settled (success or error). */
export function useWorkflowsPanelData() {
  const workflows = useQuery({
    queryKey: workflowsQueryKey,
    queryFn: ({ signal }) => listWorkflows(signal),
  });
  const groups = useQuery({
    queryKey: workflowGroupsQueryKey,
    queryFn: ({ signal }) => listWorkflowGroups(signal),
  });
  const agents = useQuery({
    queryKey: linkableAgentsQueryKey,
    queryFn: () => fetchLinkableAgents(),
  });

  const failure = [workflows, groups, agents].find((q) => q.isError)?.error;
  return {
    items: workflows.data,
    groups: groups.data,
    availableAgents: agents.data,
    loaded: ![workflows, groups, agents].some((q) => q.isPending),
    loadError: failure
      ? failure instanceof Error
        ? failure.message
        : 'Failed to load the agent catalog.'
      : null,
  };
}
