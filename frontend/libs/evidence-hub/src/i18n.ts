import type { Resource } from '@openjcockpit/i18n';
import en from './locales/en.json';

export const evidenceResources: Resource = { en };

declare module '@openjcockpit/i18n' {
  interface Resources {
    evidence: (typeof en)['evidence'];
  }
}
