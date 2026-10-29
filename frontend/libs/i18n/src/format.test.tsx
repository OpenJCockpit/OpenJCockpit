import { renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { formatDateTime, formatNumber, formatTime, useFormatters } from './format';
import { I18nProvider } from './I18nProvider';
import { createI18n } from './createI18n';

const instant = new Date('2026-03-04T15:06:07Z');

describe('format helpers', () => {
  it('formats date and time with the given locale', () => {
    const text = formatDateTime(instant, 'en', {
      dateStyle: 'short',
      timeStyle: 'short',
      timeZone: 'UTC',
    });
    expect(text).toBe('3/4/26, 3:06 PM');
  });

  it('formats date and time with default options', () => {
    expect(formatDateTime(instant, 'en')).toContain('2026');
  });

  it('formats time only, accepting numbers and strings', () => {
    const options = { timeStyle: 'medium', timeZone: 'UTC', hour12: false } as const;
    expect(formatTime(instant, 'en', options)).toBe('15:06:07');
    expect(formatTime(instant.getTime(), 'en', options)).toBe('15:06:07');
    expect(formatTime('2026-03-04T15:06:07Z', 'en', options)).toBe('15:06:07');
  });

  it('formats time with default options', () => {
    expect(formatTime(instant, 'en')).toMatch(/\d/);
  });

  it('formats numbers', () => {
    expect(formatNumber(1234.5, 'en')).toBe('1,234.5');
    expect(formatNumber(0.5, 'en', { style: 'percent' })).toBe('50%');
  });

  it('useFormatters binds the active locale', () => {
    const { result } = renderHook(() => useFormatters(), {
      wrapper: ({ children }) => <I18nProvider i18n={createI18n()}>{children}</I18nProvider>,
    });
    expect(result.current.formatNumber(1234.5)).toBe('1,234.5');
    expect(result.current.formatNumber(0.5, { style: 'percent' })).toBe('50%');
    expect(result.current.formatTime(instant, { timeStyle: 'short', timeZone: 'UTC' })).toBe(
      '3:06 PM',
    );
    expect(result.current.formatDateTime(instant)).toContain('2026');
  });
});
