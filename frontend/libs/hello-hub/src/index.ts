import type { AppDefinition } from '@openjcockpit/hub-contract';

export const helloApp: AppDefinition = {
  id: 'hello',
  labelKey: 'apps.hello.label',
  icon: 'hand',
  route: '/hello',
  order: 100,
  requiredPermissions: [],
  showInNav: false,
  loadRoutes: () => import('./helloRoutes').then((m) => m.helloRoutes),
  widgets: [
    {
      id: 'hello-widget',
      titleKey: 'apps.hello.widgetTitle',
      sourceAppId: 'hello',
      requiredPermissions: [],
      defaultFor: [],
      link: '/hello',
      component: () => import('./HelloWidget'),
    },
  ],
};
