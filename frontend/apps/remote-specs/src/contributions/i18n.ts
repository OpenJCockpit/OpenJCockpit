import type { Resource } from '@openjcockpit/i18n';
import en from '../locales/en.json';

export const specsContributionsResources: Resource = { en };

declare module '@openjcockpit/i18n' {
  interface Resources {
    specsContributions: (typeof en)['specsContributions'];
  }
}
