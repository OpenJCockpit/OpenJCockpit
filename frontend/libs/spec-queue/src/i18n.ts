import type { Resource } from '@openjcockpit/i18n';
import en from './locales/en.json';

export const specQueueResources: Resource = { en };

declare module '@openjcockpit/i18n' {
  interface Resources {
    specQueue: (typeof en)['specQueue'];
  }
}
