import { act, render, renderHook, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createI18n } from './createI18n';
import { I18nProvider } from './I18nProvider';
import { useLocale } from './LocaleContext';
import { LOCALE_STORAGE_KEY } from './locale';
import { useTranslation } from './index';
import { renderWithI18n } from './testing';

function Greeting() {
  const { t } = useTranslation();
  // Injected at runtime, so the typed key set (en.json) does not know it.
  // eslint-disable-next-line @typescript-eslint/no-unnecessary-type-assertion -- the rule misreads the empty (`never`) key union
  return <p>{t('greeting' as never)}</p>;
}

describe('I18nProvider', () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.lang = '';
  });

  it('translates from bundled and injected resources', () => {
    const i18n = createI18n({ resources: { en: { common: { greeting: 'Hello' } } } });
    render(
      <I18nProvider i18n={i18n}>
        <Greeting />
      </I18nProvider>,
    );
    expect(screen.getByText('Hello')).toBeInTheDocument();
  });

  it('sets <html lang> from the active locale', () => {
    renderWithI18n(<p>content</p>);
    expect(document.documentElement.lang).toBe('en');
  });

  it('falls back to en when the instance language is unsupported', () => {
    const i18n = createI18n();
    i18n.language = 'xx';
    renderHook(() => useLocale(), {
      wrapper: ({ children }) => <I18nProvider i18n={i18n}>{children}</I18nProvider>,
    });
    expect(document.documentElement.lang).toBe('en');
  });

  it('setLocale switches the language, persists it and updates <html lang>', async () => {
    const i18n = createI18n();
    const { result } = renderHook(() => useLocale(), {
      wrapper: ({ children }) => <I18nProvider i18n={i18n}>{children}</I18nProvider>,
    });
    expect(result.current.locale).toBe('en');
    await act(async () => {
      await result.current.setLocale('en');
    });
    expect(window.localStorage.getItem(LOCALE_STORAGE_KEY)).toBe('en');
    expect(document.documentElement.lang).toBe('en');
  });

  it('loads a locale on first selection and translates with it', async () => {
    const i18n = createI18n({ resources: { en: { common: { greeting: 'Hello' } } } });
    const loader = vi.fn().mockResolvedValue({ nl: { common: { greeting: 'Hallo' } } });
    const { result } = renderHook(() => ({ locale: useLocale(), t: useTranslation().t }), {
      wrapper: ({ children }) => (
        <I18nProvider i18n={i18n} loader={loader}>
          {children}
        </I18nProvider>
      ),
    });
    await act(async () => {
      await result.current.locale.setLocale('nl');
    });
    expect(result.current.locale.locale).toBe('nl');
    // eslint-disable-next-line @typescript-eslint/no-unnecessary-type-assertion -- the rule misreads the empty (`never`) key union
    expect(result.current.t('greeting' as never)).toBe('Hallo');
    expect(document.documentElement.lang).toBe('nl');
    expect(window.localStorage.getItem(LOCALE_STORAGE_KEY)).toBe('nl');
  });

  it('keeps the current language when the locale fails to load', async () => {
    const i18n = createI18n();
    const loader = vi.fn().mockRejectedValue(new Error('offline'));
    const { result } = renderHook(() => useLocale(), {
      wrapper: ({ children }) => (
        <I18nProvider i18n={i18n} loader={loader}>
          {children}
        </I18nProvider>
      ),
    });
    await act(async () => {
      await expect(result.current.setLocale('nl')).rejects.toThrow('offline');
    });
    expect(result.current.locale).toBe('en');
    expect(window.localStorage.getItem(LOCALE_STORAGE_KEY)).toBeNull();
  });

  it('useLocale throws outside the provider', () => {
    expect(() => renderHook(() => useLocale())).toThrow(
      'useLocale must be used inside <I18nProvider>',
    );
  });
});
