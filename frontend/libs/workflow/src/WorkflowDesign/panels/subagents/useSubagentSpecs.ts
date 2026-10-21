import { useQuery } from '@tanstack/react-query';
import { listSubagentSpecs } from '@openjcockpit/api';

export const subagentSpecsQueryKey = ['subagent-specs'] as const;

export function useSubagentSpecs() {
  return useQuery({
    queryKey: subagentSpecsQueryKey,
    queryFn: ({ signal }) => listSubagentSpecs(signal),
  });
}
