import { act, render, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { createI18n, I18nProvider } from '@openjcockpit/i18n';
import { WithSpecFilesTranslations } from './translations';

describe('WithSpecFilesTranslations', () => {
  it('registers English at once and follows the host language from its own locale files', async () => {
    const i18n = createI18n({ locale: 'en', resources: {} });
    await act(async () => {
      render(
        <I18nProvider i18n={i18n}>
          <WithSpecFilesTranslations>
            <p>child</p>
          </WithSpecFilesTranslations>
        </I18nProvider>,
      );
    });
    expect(i18n.hasResourceBundle('en', 'specFiles')).toBe(true);
    for (const language of ['de', 'es', 'nl']) {
      await act(async () => {
        await i18n.changeLanguage(language);
      });
      await waitFor(() => expect(i18n.hasResourceBundle(language, 'specFiles')).toBe(true));
    }
  });
});
