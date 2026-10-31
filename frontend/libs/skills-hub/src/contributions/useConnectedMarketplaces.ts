import { useQuery } from '@tanstack/react-query';
import { listSkillsMarketplaces } from '@openjcockpit/api';

/** Shared with the settings page, so the badge, the widget and the page read one cache entry. */
export const MARKETPLACES_KEY = ['skills-marketplaces'];

/** Number of enabled marketplace connections, or null while unknown. */
export function useConnectedMarketplaces(): number | null {
  const { data } = useQuery({
    queryKey: MARKETPLACES_KEY,
    queryFn: () => listSkillsMarketplaces(),
  });
  return data ? data.filter((connection) => connection.enabled).length : null;
}
