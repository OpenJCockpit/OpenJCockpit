import { act, render, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { createI18n, I18nProvider } from '@openjcockpit/i18n';
import { WithWorkflowTranslations } from './translations';

describe('WithWorkflowTranslations', () => {
  it('registers English at once and follows the host language from its own locale files', async () => {
    const i18n = createI18n({ locale: 'en', resources: {} });
    await act(async () => {
      render(
        <I18nProvider i18n={i18n}>
          <WithWorkflowTranslations>
            <p>child</p>
          </WithWorkflowTranslations>
        </I18nProvider>,
      );
    });
    expect(i18n.hasResourceBundle('en', 'workflow')).toBe(true);
    for (const language of ['de', 'es', 'nl']) {
      await act(async () => {
        await i18n.changeLanguage(language);
      });
      await waitFor(() => expect(i18n.hasResourceBundle(language, 'workflow')).toBe(true));
    }
  });
});
