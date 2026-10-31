import i18next, { type i18n, type Resource } from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from './locales/en.json';
import { FALLBACK_LOCALE, type Locale } from './locale';

export const DEFAULT_NAMESPACE = 'common';

export interface CreateI18nOptions {
  locale?: Locale;
  /** Extra namespaces merged next to the bundled English (feature libs, tests). */
  resources?: Resource;
}

/**
 * Builds an isolated i18next instance; each call is independent so tests never share state.
 * English is bundled as the source and fallback locale. Other locales are lazy-loaded
 * with `loadLocale`.
 */
export function createI18n({
  locale = FALLBACK_LOCALE,
  resources = {},
}: CreateI18nOptions = {}): i18n {
  const instance = i18next.createInstance();
  // Resources are inline, so init completes synchronously and callers need no loading state.
  void instance.use(initReactI18next).init({
    initAsync: false,
    lng: locale,
    fallbackLng: FALLBACK_LOCALE,
    defaultNS: DEFAULT_NAMESPACE,
    ns: [DEFAULT_NAMESPACE],
    resources: { ...resources, en: { ...en, ...resources.en } },
    interpolation: { escapeValue: false },
  });
  return instance;
}
