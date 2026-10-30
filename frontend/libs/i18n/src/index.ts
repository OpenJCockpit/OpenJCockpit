export type { Resources } from './resources';
export { createI18n, DEFAULT_NAMESPACE, type CreateI18nOptions } from './createI18n';
export { I18nProvider } from './I18nProvider';
export { loadLocale, type LocaleLoader } from './loadLocale';
export { mergeResources } from './mergeResources';
export { useLocale, type LocaleContextValue } from './LocaleContext';
export { formatDateTime, formatNumber, formatTime, useFormatters } from './format';
export {
  detectLocale,
  FALLBACK_LOCALE,
  isLocale,
  LOCALE_NAMES,
  LOCALE_STORAGE_KEY,
  SUPPORTED_LOCALES,
  type Locale,
} from './locale';
export { useTranslation, Trans } from 'react-i18next';
export type { Resource, TFunction } from 'i18next';
