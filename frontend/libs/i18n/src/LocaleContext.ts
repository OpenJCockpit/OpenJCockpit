import { createContext, useContext } from 'react';
import type { Locale } from './locale';

export interface LocaleContextValue {
  locale: Locale;
  setLocale: (locale: Locale) => Promise<void>;
}

export const LocaleContext = createContext<LocaleContextValue | null>(null);

/** Active locale plus a setter that switches the language and remembers the choice. */
export function useLocale(): LocaleContextValue {
  const value = useContext(LocaleContext);
  if (!value) throw new Error('useLocale must be used inside <I18nProvider>');
  return value;
}
