import { fireEvent, render as rtlRender, screen, waitFor, within } from '@testing-library/react';
import {
  createI18n,
  I18nProvider,
  LOCALE_STORAGE_KEY,
  type LocaleLoader,
} from '@openjcockpit/i18n';
import { FakeHubThemeProvider } from '@openjcockpit/hub-contract/testing';
import { describe, expect, it, vi } from 'vitest';
import { dashboardResources } from '../i18n';
import nl from '../locales/nl.json';
import PreferencesSection from './PreferencesSection';

function renderSection(i18n: ReturnType<typeof createI18n>, loader: LocaleLoader) {
  rtlRender(
    <I18nProvider i18n={i18n} loader={loader}>
      <PreferencesSection />
    </I18nProvider>,
  );
}

function chooseLanguage(name: string) {
  fireEvent.click(screen.getByRole('combobox', { name: 'Language' }));
  fireEvent.click(screen.getByRole('option', { name }));
}

describe('PreferencesSection', () => {
  it('offers every supported language by its own name', () => {
    renderSection(createI18n({ resources: dashboardResources }), vi.fn());
    const select = screen.getByRole('combobox', { name: 'Language' });
    expect(select).toHaveTextContent('English');
    fireEvent.click(select);
    expect(
      within(screen.getByRole('listbox')).getByRole('option', { name: 'Nederlands' }),
    ).toHaveAttribute('lang', 'nl');
  });

  it('switches the language, translating the labels and remembering the choice', async () => {
    window.localStorage.clear();
    renderSection(createI18n({ resources: dashboardResources }), vi.fn().mockResolvedValue({ nl }));
    chooseLanguage('Nederlands');
    expect(await screen.findByRole('combobox', { name: 'Taal' })).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByTestId('locale-select')).toHaveTextContent('Nederlands'),
    );
    expect(document.documentElement.lang).toBe('nl');
    expect(window.localStorage.getItem(LOCALE_STORAGE_KEY)).toBe('nl');
  });

  it('stays on the current language when the translations cannot be downloaded', async () => {
    const loader = vi.fn().mockRejectedValue(new Error('offline'));
    renderSection(createI18n({ resources: dashboardResources }), loader);
    chooseLanguage('Nederlands');
    await waitFor(() => expect(loader).toHaveBeenCalled());
    expect(screen.getByTestId('locale-select')).toHaveTextContent('English');
  });

  it('offers a theme choice only when the host provides a theme', () => {
    renderSection(createI18n({ resources: dashboardResources }), vi.fn());
    expect(screen.queryByTestId('theme-select')).not.toBeInTheDocument();
  });

  it('shows the host theme and changes it', () => {
    const onChange = vi.fn();
    rtlRender(
      <I18nProvider i18n={createI18n({ resources: dashboardResources })} loader={vi.fn()}>
        <FakeHubThemeProvider preference="dark" onChange={onChange}>
          <PreferencesSection />
        </FakeHubThemeProvider>
      </I18nProvider>,
    );
    expect(screen.getByTestId('theme-select')).toHaveTextContent('Dark');
    fireEvent.click(screen.getByRole('combobox', { name: 'Theme' }));
    fireEvent.click(screen.getByRole('option', { name: 'Light' }));
    expect(onChange).toHaveBeenCalledWith('light');
  });
});
