import type { Resource } from '@openjcockpit/i18n';
import en from './locales/en.json';

export const workflowResources: Resource = { en };

declare module '@openjcockpit/i18n' {
  interface Resources {
    workflow: (typeof en)['workflow'];
  }
}
