import type { AppDefinition } from '@openjcockpit/hub-contract';
import { SKILLS_HUB_ROUTE } from './skillsHubRoutes';
import { useConnectedMarketplaces } from './contributions/useConnectedMarketplaces';

export const skillsHubApp: AppDefinition = {
  id: 'skills-hub',
  labelKey: 'siteHeader.nav.skillsHub',
  icon: 'skillsHub',
  route: SKILLS_HUB_ROUTE,
  order: 50,
  requiredPermissions: [],
  useBadge: useConnectedMarketplaces,
  loadRoutes: () => import('./skillsHubRoutes').then((m) => m.skillsHubRoutes),
  widgets: [
    {
      id: 'skills-hub-marketplaces',
      titleKey: 'siteHeader.nav.skillsHub',
      sourceAppId: 'skills-hub',
      requiredPermissions: [],
      defaultFor: [],
      link: SKILLS_HUB_ROUTE,
      component: () => import('./contributions/SkillsHubWidget'),
    },
  ],
};
