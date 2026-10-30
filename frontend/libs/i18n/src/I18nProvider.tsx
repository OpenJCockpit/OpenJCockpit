import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { I18nextProvider } from 'react-i18next';
import type { i18n } from 'i18next';
import { LocaleContext } from './LocaleContext';
import { loadLocale, type LocaleLoader } from './loadLocale';
import { applyDocumentLanguage, isLocale, persistLocale, type Locale } from './locale';

interface I18nProviderProps {
  i18n: i18n;
  /** Fetches the translations of a non-English locale the first time it is selected. */
  loader?: LocaleLoader;
  children: ReactNode;
}

/** Provides translations and the locale switcher, and mirrors the active locale onto `<html lang>`. */
export function I18nProvider({ i18n, loader, children }: I18nProviderProps) {
  const [locale, setLocaleState] = useState<Locale>(() =>
    isLocale(i18n.language) ? i18n.language : 'en',
  );

  useEffect(() => {
    applyDocumentLanguage(locale);
  }, [locale]);

  const setLocale = useCallback(
    async (next: Locale) => {
      await loadLocale(i18n, next, loader);
      await i18n.changeLanguage(next);
      persistLocale(next);
      setLocaleState(next);
    },
    [i18n, loader],
  );

  const value = useMemo(() => ({ locale, setLocale }), [locale, setLocale]);

  return (
    <I18nextProvider i18n={i18n}>
      <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>
    </I18nextProvider>
  );
}
