import type { AppDefinition } from '@openjcockpit/hub-contract';
import { EVIDENCE_ROUTE } from './evidenceRoutes';

export const evidenceApp: AppDefinition = {
  id: 'evidence',
  labelKey: 'siteHeader.nav.evidence',
  icon: 'pulse',
  route: EVIDENCE_ROUTE,
  order: 60,
  requiredPermissions: [],
  showInNav: true,
  loadRoutes: () => import('./evidenceRoutes').then((m) => m.evidenceRoutes),
  widgets: [
    {
      id: 'evidence-status',
      titleKey: 'apps.evidence.widgetTitle',
      sourceAppId: 'evidence',
      requiredPermissions: [],
      defaultFor: [],
      link: EVIDENCE_ROUTE,
      cols: 2,
      rows: 2,
      component: () => import('./contributions/EvidenceWidget'),
    },
  ],
};
