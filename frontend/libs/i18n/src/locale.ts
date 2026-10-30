/** Pseudo-locale (accented, padded English) for spotting un-externalised strings and overflow; dev builds only. */
export const PSEUDO_LOCALE = 'en-XA';

const REAL_LOCALES = ['en', 'nl', 'de', 'es'] as const;
export type Locale = (typeof REAL_LOCALES)[number] | typeof PSEUDO_LOCALE;

export const SUPPORTED_LOCALES: readonly Locale[] = import.meta.env.DEV
  ? [...REAL_LOCALES, PSEUDO_LOCALE]
  : REAL_LOCALES;

/** Name of each locale in its own language, as shown in the language selector. */
export const LOCALE_NAMES: Record<Locale, string> = {
  en: 'English',
  nl: 'Nederlands',
  de: 'Deutsch',
  es: 'Español',
  [PSEUDO_LOCALE]: 'Pseudo (dev)',
};

export const FALLBACK_LOCALE: Locale = 'en';
export const LOCALE_STORAGE_KEY = 'openjcockpit.locale';

export function isLocale(value: unknown): value is Locale {
  return SUPPORTED_LOCALES.some((locale) => locale === value);
}

/** Maps a BCP 47 tag such as `nl-BE` to a supported locale, or undefined. */
function matchLocale(tag: string): Locale | undefined {
  const language = tag.toLowerCase().split('-')[0];
  return SUPPORTED_LOCALES.find((locale) => locale.toLowerCase() === language);
}

function readStoredLocale(): Locale | undefined {
  try {
    const stored = window.localStorage.getItem(LOCALE_STORAGE_KEY);
    return isLocale(stored) ? stored : undefined;
  } catch {
    // Storage can be blocked (private mode, policy); detection then falls through.
    return undefined;
  }
}

/** Saved choice first, then the browser's language preferences, then English. */
export function detectLocale(): Locale {
  const stored = readStoredLocale();
  if (stored) return stored;
  const preferred = window.navigator.languages?.length
    ? window.navigator.languages
    : [window.navigator.language];
  for (const tag of preferred) {
    const match = matchLocale(tag);
    if (match) return match;
  }
  return FALLBACK_LOCALE;
}

export function persistLocale(locale: Locale): void {
  try {
    window.localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  } catch {
    // The choice still applies for this session; it just will not survive a reload.
  }
}

/** Keeps `<html lang>` in step with the UI language for screen readers and hyphenation. */
export function applyDocumentLanguage(locale: Locale): void {
  document.documentElement.lang = locale;
}
