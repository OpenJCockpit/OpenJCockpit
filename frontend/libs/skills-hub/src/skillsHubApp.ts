import type { AppDefinition } from '@openjcockpit/hub-contract';
import { SKILLS_HUB_ROUTE } from './skillsHubRoutes';

export const skillsHubApp: AppDefinition = {
  id: 'skills-hub',
  labelKey: 'siteHeader.nav.skillsHub',
  icon: 'skillsHub',
  route: SKILLS_HUB_ROUTE,
  order: 50,
  requiredPermissions: [],
  loadRoutes: () => import('./skillsHubRoutes').then((m) => m.skillsHubRoutes),
};
