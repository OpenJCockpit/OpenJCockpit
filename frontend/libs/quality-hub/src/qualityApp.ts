import type { AppDefinition } from '@openjcockpit/hub-contract';
import { QUALITY_ROUTE } from './qualityRoutes';

export const qualityApp: AppDefinition = {
  id: 'quality',
  labelKey: 'siteHeader.nav.quality',
  icon: 'quality',
  route: QUALITY_ROUTE,
  order: 70,
  requiredPermissions: [],
  showInNav: true,
  loadRoutes: () => import('./qualityRoutes').then((m) => m.qualityRoutes),
  widgets: [
    {
      id: 'quality-status',
      titleKey: 'apps.quality.widgetTitle',
      sourceAppId: 'quality',
      requiredPermissions: [],
      defaultFor: [],
      link: QUALITY_ROUTE,
      cols: 1,
      minCols: 1,
      rows: 2,
      component: () => import('./contributions/QualityWidget'),
    },
  ],
};
