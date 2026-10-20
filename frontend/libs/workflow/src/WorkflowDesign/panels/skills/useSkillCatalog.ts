import { useQuery } from '@tanstack/react-query';
import { getSkillCatalog } from '@openjcockpit/api';

export const skillCatalogQueryKey = ['skill-catalog'] as const;

export function useSkillCatalog() {
  return useQuery({ queryKey: skillCatalogQueryKey, queryFn: () => getSkillCatalog() });
}
