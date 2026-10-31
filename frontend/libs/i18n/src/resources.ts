import type en from './locales/en.json';

/**
 * Compile-time shape of the English resources, so translation keys are checked against real keys.
 * Each feature lib adds its namespace by augmenting this interface:
 *
 *   declare module '@openjcockpit/i18n' {
 *     interface Resources { landing: typeof import('./locales/en.json').landing }
 *   }
 */
export interface Resources {
  common: (typeof en)['common'];
}

declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'common';
    resources: Resources;
  }
}
