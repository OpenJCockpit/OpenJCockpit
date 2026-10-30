import type { AppDefinition } from '@openjcockpit/hub-contract';
import { usePulseBadge } from './usePulseBadge';

export const pulseApp: AppDefinition = {
  id: 'pulse',
  labelKey: 'apps.pulse.label',
  icon: 'pulse',
  route: '/pulse',
  order: 110,
  requiredPermissions: [],
  useBadge: usePulseBadge,
  loadRoutes: () => import('./pulseRoutes').then((m) => m.pulseRoutes),
  widgets: [
    {
      id: 'pulse-status',
      titleKey: 'apps.pulse.widget.title',
      sourceAppId: 'pulse',
      requiredPermissions: [],
      component: () => import('./PulseWidget').then((m) => ({ default: m.PulseWidget })),
      defaultFor: [],
      link: '/pulse',
    },
  ],
  quickActions: [
    {
      id: 'pulse-open',
      labelKey: 'apps.pulse.quickAction.open',
      icon: 'pulse',
      requiredPermissions: [],
      link: '/pulse',
    },
  ],
  topbar: [
    {
      id: 'pulse-chip',
      requiredPermissions: [],
      component: () => import('./PulseTopbar').then((m) => ({ default: m.PulseTopbar })),
    },
  ],
};
