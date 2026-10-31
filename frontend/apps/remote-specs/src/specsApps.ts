import type { AppDefinition, SettingsSectionDefinition } from '@openjcockpit/hub-contract';
import { useQueueAttention } from './contributions/useQueueAttention';
import { useSpecFileBadge } from './contributions/useSpecFileCount';
import { useWorkflowCount } from './contributions/useWorkflowCount';
import { SPEC_FILES_ROUTE, SPEC_QUEUE_ROUTE, WORKFLOW_ROUTE } from './routePaths';

type WorkflowSettingsExport = keyof typeof import('./contributions/workflowSettings');

/** Titles and descriptions live in the host's catalog, like widget titles. */
function workflowSection(
  section: Pick<
    SettingsSectionDefinition,
    'id' | 'titleKey' | 'descriptionKey' | 'order' | 'groupKey' | 'requiresProject'
  >,
  exportName: WorkflowSettingsExport,
): SettingsSectionDefinition {
  return {
    ...section,
    sourceAppId: 'workflow',
    screen: section.requiresProject ? 'project' : 'settings',
    requiredPermissions: [],
    component: () =>
      import('./contributions/workflowSettings').then((m) => ({ default: m[exportName] })),
  };
}

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
    settings: [
      workflowSection(
        {
          id: 'workflow-opa',
          titleKey: 'settings.sections.workflowOpa.title',
          descriptionKey: 'settings.sections.workflowOpa.description',
          order: 110,
          groupKey: 'settings.groups.integrations',
          requiresProject: false,
        },
        'OpaSection',
      ),
      workflowSection(
        {
          id: 'workflow-a2a',
          titleKey: 'settings.sections.workflowA2a.title',
          descriptionKey: 'settings.sections.workflowA2a.description',
          order: 120,
          groupKey: 'settings.groups.integrations',
          requiresProject: false,
        },
        'A2ASection',
      ),
      workflowSection(
        {
          id: 'workflow-hermes',
          titleKey: 'settings.sections.workflowHermes.title',
          descriptionKey: 'settings.sections.workflowHermes.description',
          order: 210,
          groupKey: 'settings.groups.projectIntegrations',
          requiresProject: true,
        },
        'HermesSection',
      ),
      workflowSection(
        {
          id: 'workflow-jira',
          titleKey: 'settings.sections.workflowJira.title',
          descriptionKey: 'settings.sections.workflowJira.description',
          order: 220,
          groupKey: 'settings.groups.projectIntegrations',
          requiresProject: true,
        },
        'JiraSection',
      ),
      workflowSection(
        {
          id: 'workflow-documents',
          titleKey: 'settings.sections.workflowDocuments.title',
          descriptionKey: 'settings.sections.workflowDocuments.description',
          order: 230,
          groupKey: 'settings.groups.projectIntegrations',
          requiresProject: true,
        },
        'DocumentsSection',
      ),
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
    settings: [
      {
        id: 'spec-queue',
        sourceAppId: 'spec-queue',
        titleKey: 'settings.sections.specQueue.title',
        descriptionKey: 'settings.sections.specQueue.description',
        requiredPermissions: [],
        order: 20,
        screen: 'project',
        groupKey: 'settings.groups.project',
        requiresProject: true,
        component: () => import('./contributions/SpecQueueSettingsSection'),
      },
    ],
  },
];
