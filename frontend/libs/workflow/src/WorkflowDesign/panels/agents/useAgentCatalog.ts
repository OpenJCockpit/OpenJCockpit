import { useQuery } from '@tanstack/react-query';
import { getAgentCatalog } from '@openjcockpit/api';

export const agentCatalogQueryKey = ['agent-catalog'] as const;

export function useAgentCatalog() {
  return useQuery({
    queryKey: agentCatalogQueryKey,
    queryFn: async () => {
      try {
        return await getAgentCatalog();
      } catch (err) {
        console.warn(err);
        throw err;
      }
    },
  });
}
