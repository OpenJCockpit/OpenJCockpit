import type { AppDefinition } from '@openjcockpit/hub-contract';

/**
 * The host's own contribution to the registry: it has no pages or nav entry, only settings
 * sections, so it goes through the same path as a remote's. The project lib is bundled with
 * the host, so its translations are already registered and the section needs no provider.
 */
export const hostApp: AppDefinition = {
  id: 'host',
  labelKey: 'siteHeader.nav.platform',
  icon: 'platform',
  route: '/host',
  order: 0,
  requiredPermissions: [],
  showInNav: false,
  loadRoutes: () => Promise.resolve([]),
  settings: [
    {
      id: 'preferences',
      sourceAppId: 'host',
      titleKey: 'settings.sections.preferences.title',
      descriptionKey: 'settings.sections.preferences.description',
      requiredPermissions: [],
      order: 0,
      groupKey: 'settings.groups.general',
      component: () => import('./settings/PreferencesSection'),
    },
    {
      id: 'projects',
      sourceAppId: 'host',
      titleKey: 'settings.sections.projects.title',
      descriptionKey: 'settings.sections.projects.description',
      requiredPermissions: [],
      order: 10,
      screen: 'project',
      groupKey: 'settings.groups.project',
      component: () =>
        import('@openjcockpit/project').then((m) => ({ default: m.ProjectSettings })),
    },
  ],
};
