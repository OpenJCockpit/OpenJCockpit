import { act, render, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { createI18n, I18nProvider } from '@openjcockpit/i18n';
import { WithContributionTranslations } from './translations';

describe('WithContributionTranslations', () => {
  it('registers English at once and follows the host language from its own locale files', async () => {
    const i18n = createI18n({ locale: 'en', resources: {} });
    await act(async () => {
      render(
        <I18nProvider i18n={i18n}>
          <WithContributionTranslations>
            <p>child</p>
          </WithContributionTranslations>
        </I18nProvider>,
      );
    });
    expect(i18n.hasResourceBundle('en', 'specsContributions')).toBe(true);
    for (const language of ['de', 'es', 'nl']) {
      await act(async () => {
        await i18n.changeLanguage(language);
      });
      await waitFor(() =>
        expect(i18n.hasResourceBundle(language, 'specsContributions')).toBe(true),
      );
    }
  });
});
