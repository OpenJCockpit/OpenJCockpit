import type { Resource } from '@openjcockpit/i18n';
import en from './locales/en.json';

export const landingResources: Resource = { en };

declare module '@openjcockpit/i18n' {
  interface Resources {
    landing: (typeof en)['landing'];
  }
}
