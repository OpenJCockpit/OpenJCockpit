import type { Resource } from '@openjcockpit/i18n';
import en from './locales/en.json';

export const dashboardResources: Resource = { en };

declare module '@openjcockpit/i18n' {
  interface Resources {
    dashboard: (typeof en)['dashboard'];
  }
}
