import type { Resource } from '@openjcockpit/i18n';
import en from './locales/en.json';

export const qualityResources: Resource = { en };

declare module '@openjcockpit/i18n' {
  interface Resources {
    quality: (typeof en)['quality'];
  }
}
