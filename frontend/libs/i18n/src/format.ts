import { useLocale } from './LocaleContext';
import type { Locale } from './locale';

type DateInput = Date | number | string;

export function formatDateTime(
  value: DateInput,
  locale: Locale,
  options: Intl.DateTimeFormatOptions = { dateStyle: 'medium', timeStyle: 'short' },
): string {
  return new Intl.DateTimeFormat(locale, options).format(new Date(value));
}

export function formatTime(
  value: DateInput,
  locale: Locale,
  options: Intl.DateTimeFormatOptions = { timeStyle: 'medium' },
): string {
  return new Intl.DateTimeFormat(locale, options).format(new Date(value));
}

export function formatNumber(
  value: number,
  locale: Locale,
  options?: Intl.NumberFormatOptions,
): string {
  return new Intl.NumberFormat(locale, options).format(value);
}

/** The formatters above, bound to the active locale. */
export function useFormatters() {
  const { locale } = useLocale();
  return {
    formatDateTime: (value: DateInput, options?: Intl.DateTimeFormatOptions) =>
      formatDateTime(value, locale, options),
    formatTime: (value: DateInput, options?: Intl.DateTimeFormatOptions) =>
      formatTime(value, locale, options),
    formatNumber: (value: number, options?: Intl.NumberFormatOptions) =>
      formatNumber(value, locale, options),
  };
}
