import type { Resource } from '@openjcockpit/i18n';
import en from './locales/en.json';

/** Labels the API layer derives from DTOs live in the `api` namespace; callers pass `useTranslation('api').t`. */
export const apiResources: Resource = { en };

declare module '@openjcockpit/i18n' {
  interface Resources {
    api: (typeof en)['api'];
  }
}
