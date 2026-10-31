import type { AppDefinition } from '@openjcockpit/hub-contract';
import { ADMIN_PERMISSION } from '@openjcockpit/hub-contract';

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
  quickActions: [
    {
      id: 'hello-open',
      labelKey: 'apps.hello.openAction',
      requiredPermissions: [],
      link: '/hello',
    },
    {
      // Proves the permission filter: only holders of the admin permission see this one.
      id: 'hello-admin',
      labelKey: 'apps.hello.adminAction',
      requiredPermissions: [ADMIN_PERMISSION],
      link: '/hello',
    },
  ],
  topbar: [
    {
      id: 'hello-topbar',
      requiredPermissions: [],
      component: () => import('./HelloTopbar'),
    },
  ],
};
