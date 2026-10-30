import { act, render, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { createI18n } from './createI18n';
import { I18nProvider } from './I18nProvider';
import { WithTranslations, type LocaleModules } from './WithTranslations';

const english = { hello: 'Hello' };
const loaders = { de: vi.fn(() => Promise.resolve({ default: { demo: { hello: 'Hallo' } } })) };
const locales: LocaleModules = { './locales/de.json': loaders.de };

async function setup(language: string, prepare?: (i18n: ReturnType<typeof createI18n>) => void) {
  const i18n = createI18n({ locale: 'en', resources: {} });
  prepare?.(i18n);
  await act(async () => {
    render(
      <I18nProvider i18n={i18n}>
        <WithTranslations namespace="demo" english={english} locales={locales}>
          <p>child</p>
        </WithTranslations>
      </I18nProvider>,
    );
  });
  await act(async () => {
    await i18n.changeLanguage(language);
  });
  return i18n;
}

describe('WithTranslations', () => {
  it('registers English at once and another language when the host switches to it', async () => {
    const i18n = await setup('de');
    expect(i18n.getResource('en', 'demo', 'hello')).toBe('Hello');
    await waitFor(() => expect(i18n.getResource('de', 'demo', 'hello')).toBe('Hallo'));
  });

  it('ignores a language it has no translations for', async () => {
    const i18n = await setup('fr');
    expect(i18n.hasResourceBundle('fr', 'demo')).toBe(false);
  });

  it('does not download a language twice', async () => {
    loaders.de.mockClear();
    const i18n = await setup('de');
    await waitFor(() => expect(i18n.hasResourceBundle('de', 'demo')).toBe(true));
    await act(async () => {
      await i18n.changeLanguage('en');
      await i18n.changeLanguage('de');
    });
    expect(loaders.de).toHaveBeenCalledOnce();
  });

  it('keeps the current translations when a download fails', async () => {
    const i18n = await setup('de', (instance) => {
      const add = instance.addResourceBundle.bind(instance);
      vi.spyOn(instance, 'addResourceBundle').mockImplementation((language, ...rest) => {
        if (language === 'de') throw new Error('chunk failed');
        return add(language, ...rest);
      });
    });
    expect(i18n.hasResourceBundle('de', 'demo')).toBe(false);
  });
});
