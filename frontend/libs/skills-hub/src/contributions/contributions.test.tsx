import { act, render, renderHook, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import * as api from '@openjcockpit/api';
import { createI18n, I18nProvider } from '@openjcockpit/i18n';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { skillsHubResources } from '../i18n';
import { skillsHubApp } from '../skillsHubApp';
import SkillsHubSettingsSection from './SkillsHubSettingsSection';
import SkillsHubWidget from './SkillsHubWidget';
import { useConnectedMarketplaces } from './useConnectedMarketplaces';

vi.mock('@openjcockpit/api', async (importOriginal) =>
  (await import('../testUtils')).automockApiClients(await importOriginal()),
);

const connection = (enabled: boolean) => ({ id: String(enabled), enabled }) as never;

function wrapper({ children }: { children: React.ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const i18n = createI18n({ locale: 'en', resources: skillsHubResources });
  return (
    <I18nProvider i18n={i18n}>
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    </I18nProvider>
  );
}

async function renderWidget() {
  await act(async () => {
    render(<SkillsHubWidget />, { wrapper });
  });
}

describe('skills hub contributions', () => {
  it('counts only enabled marketplaces for the badge', async () => {
    vi.mocked(api.listSkillsMarketplaces).mockResolvedValue([connection(true), connection(false)]);
    const { result } = renderHook(() => useConnectedMarketplaces(), { wrapper });
    expect(result.current).toBeNull();
    await waitFor(() => expect(result.current).toBe(1));
  });

  it('shows the connected count in the widget', async () => {
    vi.mocked(api.listSkillsMarketplaces).mockResolvedValue([connection(true), connection(true)]);
    await renderWidget();
    expect(await screen.findByText('Connected marketplaces: 2')).toBeInTheDocument();
  });

  it('shows an empty message when nothing is connected', async () => {
    vi.mocked(api.listSkillsMarketplaces).mockResolvedValue([]);
    await renderWidget();
    expect(await screen.findByText('No marketplace connected yet.')).toBeInTheDocument();
  });

  it('renders nothing in the widget until the count is known', async () => {
    vi.mocked(api.listSkillsMarketplaces).mockReturnValue(new Promise(() => undefined));
    await renderWidget();
    expect(screen.queryByTestId('skills-hub-widget')).toBeNull();
  });

  it('renders the marketplace settings with its own translations', async () => {
    vi.mocked(api.listSkillsMarketplaces).mockResolvedValue([]);
    await act(async () => {
      render(<SkillsHubSettingsSection />, { wrapper });
    });
    expect(await screen.findByText('New marketplace')).toBeInTheDocument();
  });

  it('declares each contribution on the app definition', async () => {
    expect(skillsHubApp.useBadge).toBe(useConnectedMarketplaces);
    expect((await skillsHubApp.widgets?.[0]?.component())?.default).toBe(SkillsHubWidget);
    expect((await skillsHubApp.settings?.[0]?.component())?.default).toBe(SkillsHubSettingsSection);
  });
});
