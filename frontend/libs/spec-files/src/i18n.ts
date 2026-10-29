import type { Resource } from '@openjcockpit/i18n';
import en from './locales/en.json';

export const specFilesResources: Resource = { en };

declare module '@openjcockpit/i18n' {
  interface Resources {
    specFiles: (typeof en)['specFiles'];
  }
}
