import type { AppDefinition } from '@openjcockpit/hub-contract';

export const SPEC_FILES_ROUTE = '/spec-files';
export const SPEC_QUEUE_ROUTE = '/spec-queue';
export const WORKFLOW_ROUTE = '/workflow';

/** Nav order, kept as it was in the host menu: platform, workflow, spec files, spec queue, skills hub. */
export const specsApps: AppDefinition[] = [
  {
    id: 'workflow',
    labelKey: 'siteHeader.nav.workflow',
    icon: 'workflow',
    route: WORKFLOW_ROUTE,
    order: 20,
    requiredPermissions: [],
    loadRoutes: () => import('./routes').then((m) => m.workflowRoutes),
  },
  {
    id: 'spec-files',
    labelKey: 'siteHeader.nav.specFiles',
    icon: 'specFiles',
    route: SPEC_FILES_ROUTE,
    order: 30,
    requiredPermissions: [],
    loadRoutes: () => import('./routes').then((m) => m.specFilesRoutes),
  },
  {
    id: 'spec-queue',
    labelKey: 'siteHeader.nav.specQueue',
    icon: 'specQueue',
    route: SPEC_QUEUE_ROUTE,
    order: 40,
    requiredPermissions: [],
    loadRoutes: () => import('./routes').then((m) => m.specQueueRoutes),
  },
];
