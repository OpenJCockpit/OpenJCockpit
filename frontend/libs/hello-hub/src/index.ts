import type { AppDefinition } from '@openjcockpit/hub-contract';

export const helloApp: AppDefinition = {
  id: 'hello',
  labelKey: 'apps.hello.label',
  icon: 'hand',
  route: '/hello',
  order: 100,
  requiredPermissions: [],
  loadRoutes: () => import('./helloRoutes').then((m) => m.helloRoutes),
};
