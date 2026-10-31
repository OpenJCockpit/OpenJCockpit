import { useLocale } from './LocaleContext';
import type { Locale } from './locale';

type DateInput = Date | number | string;

/** Values that are not a parseable date (e.g. "Yesterday") are shown as received instead of throwing. */
function formatDateValue(
  value: DateInput,
  locale: Locale,
  options: Intl.DateTimeFormatOptions,
): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? String(value)
    : new Intl.DateTimeFormat(locale, options).format(date);
}

export function formatDateTime(
  value: DateInput,
  locale: Locale,
  options: Intl.DateTimeFormatOptions = { dateStyle: 'medium', timeStyle: 'short' },
): string {
  return formatDateValue(value, locale, options);
}

export function formatDate(
  value: DateInput,
  locale: Locale,
  options: Intl.DateTimeFormatOptions = { dateStyle: 'medium' },
): string {
  return formatDateValue(value, locale, options);
}

export function formatTime(
  value: DateInput,
  locale: Locale,
  options: Intl.DateTimeFormatOptions = { timeStyle: 'medium' },
): string {
  return formatDateValue(value, locale, options);
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
    formatDate: (value: DateInput, options?: Intl.DateTimeFormatOptions) =>
      formatDate(value, locale, options),
    formatTime: (value: DateInput, options?: Intl.DateTimeFormatOptions) =>
      formatTime(value, locale, options),
    formatNumber: (value: number, options?: Intl.NumberFormatOptions) =>
      formatNumber(value, locale, options),
  };
}
