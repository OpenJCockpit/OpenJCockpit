import type { AppDefinition } from '@openjcockpit/hub-contract';
import { useQueueAttention } from './contributions/useQueueAttention';
import { useSpecFileBadge } from './contributions/useSpecFileCount';
import { useWorkflowCount } from './contributions/useWorkflowCount';
import { SPEC_FILES_ROUTE, SPEC_QUEUE_ROUTE, WORKFLOW_ROUTE } from './routePaths';

/** Nav order, kept as it was in the host menu: platform, workflow, spec files, spec queue, skills hub. */
export const specsApps: AppDefinition[] = [
  {
    id: 'workflow',
    labelKey: 'siteHeader.nav.workflow',
    icon: 'workflow',
    route: WORKFLOW_ROUTE,
    order: 20,
    requiredPermissions: [],
    useBadge: useWorkflowCount,
    loadRoutes: () => import('./routes').then((m) => m.workflowRoutes),
    widgets: [
      {
        id: 'workflow-recent',
        titleKey: 'apps.workflow.widgetTitle',
        sourceAppId: 'workflow',
        requiredPermissions: [],
        defaultFor: [],
        link: WORKFLOW_ROUTE,
        component: () => import('./contributions/WorkflowsWidget'),
      },
    ],
    quickActions: [
      {
        id: 'workflow-open',
        labelKey: 'apps.workflow.openAction',
        requiredPermissions: [],
        link: WORKFLOW_ROUTE,
      },
    ],
    topbar: [
      {
        id: 'workflow-run-topbar',
        requiredPermissions: [],
        component: () => import('./contributions/WorkflowRunTopbar'),
      },
    ],
  },
  {
    id: 'spec-files',
    labelKey: 'siteHeader.nav.specFiles',
    icon: 'specFiles',
    route: SPEC_FILES_ROUTE,
    order: 30,
    requiredPermissions: [],
    useBadge: useSpecFileBadge,
    loadRoutes: () => import('./routes').then((m) => m.specFilesRoutes),
    widgets: [
      {
        id: 'spec-files-count',
        titleKey: 'apps.specFiles.widgetTitle',
        sourceAppId: 'spec-files',
        requiredPermissions: [],
        defaultFor: [],
        link: SPEC_FILES_ROUTE,
        component: () => import('./contributions/SpecFilesWidget'),
      },
    ],
    quickActions: [
      {
        id: 'spec-files-open',
        labelKey: 'apps.specFiles.openAction',
        requiredPermissions: [],
        link: SPEC_FILES_ROUTE,
      },
    ],
  },
  {
    id: 'spec-queue',
    labelKey: 'siteHeader.nav.specQueue',
    icon: 'specQueue',
    route: SPEC_QUEUE_ROUTE,
    order: 40,
    requiredPermissions: [],
    useBadge: useQueueAttention,
    loadRoutes: () => import('./routes').then((m) => m.specQueueRoutes),
    widgets: [
      {
        id: 'spec-queue-status',
        titleKey: 'apps.specQueue.widgetTitle',
        sourceAppId: 'spec-queue',
        requiredPermissions: [],
        defaultFor: [],
        link: SPEC_QUEUE_ROUTE,
        component: () => import('./contributions/SpecQueueWidget'),
      },
    ],
    quickActions: [
      {
        id: 'spec-queue-open',
        labelKey: 'apps.specQueue.openAction',
        requiredPermissions: [],
        link: SPEC_QUEUE_ROUTE,
      },
    ],
    topbar: [
      {
        id: 'spec-queue-topbar',
        requiredPermissions: [],
        component: () => import('./contributions/SpecQueueTopbar'),
      },
    ],
  },
];
