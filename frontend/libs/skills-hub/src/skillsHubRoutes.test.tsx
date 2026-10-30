import { act, screen, waitFor } from '@testing-library/react';
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import * as api from '@openjcockpit/api';
import { createI18n, I18nProvider } from '@openjcockpit/i18n';
import { DASHBOARD_PATH, HubHeaderContext, isAppDefinition } from '@openjcockpit/hub-contract';
import { FakeHubProjectProvider } from '@openjcockpit/hub-contract/testing';
import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { skillsHubApp } from './index';
import { skillsHubRoutes } from './skillsHubRoutes';
import { WithTranslations } from './registerTranslations';
import { skillsHubResources } from './i18n';
import type { Project } from '@openjcockpit/api';
import { clickAsync } from './testUtils';

vi.mock('@openjcockpit/api', async (importOriginal) =>
  (await import('./testUtils')).automockApiClients(await importOriginal()),
);

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>;
}

async function renderApp(options: { at: string; selected?: boolean; header?: boolean }) {
  vi.mocked(api.listSkillsMarketplaces).mockResolvedValue([]);
  const router = createMemoryRouter(
    [
      { path: '/skills-hub', children: skillsHubRoutes },
      { path: '*', element: <Where /> },
    ],
    { initialEntries: [options.at] },
  );
  const i18n = createI18n({ locale: 'en', resources: skillsHubResources });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const project = options.selected ? ({ id: 'p', name: 'P' } as Project) : null;
  await act(async () => {
    render(
      <I18nProvider i18n={i18n}>
        <QueryClientProvider client={client}>
          <HubHeaderContext.Provider value={options.header ? <p>host header</p> : undefined}>
            <FakeHubProjectProvider selected={project}>
              <RouterProvider router={router} />
            </FakeHubProjectProvider>
          </HubHeaderContext.Provider>
        </QueryClientProvider>
      </I18nProvider>,
    );
  });
  return router;
}

describe('skillsHubApp', () => {
  it('is a valid app definition mounted at /skills-hub', () => {
    expect(isAppDefinition(skillsHubApp)).toBe(true);
    expect(skillsHubApp).toMatchObject({ id: 'skills-hub', route: '/skills-hub' });
  });

  it('loads its routes lazily', async () => {
    await expect(skillsHubApp.loadRoutes()).resolves.toBe(skillsHubRoutes);
  });
});

describe('skillsHubRoutes', () => {
  it('shows the host header instead of its own when the host provides one', async () => {
    await renderApp({ at: '/skills-hub', header: true });
    expect(screen.getByText('host header')).toBeInTheDocument();
  });

  it('falls back to its own back header without a host', async () => {
    await renderApp({ at: '/skills-hub' });
    expect(screen.queryByText('host header')).not.toBeInTheDocument();
    expect(screen.getByTitle('Back to dashboard')).toBeInTheDocument();
  });

  it.each([
    [true, DASHBOARD_PATH],
    [false, '/projects'],
  ])('goes back (project selected: %s) to %s', async (selected, target) => {
    const router = await renderApp({ at: '/skills-hub', selected });
    await clickAsync(screen.getByTitle('Back to dashboard'));
    expect(router.state.location.pathname).toBe(target);
  });

  it('opens the settings and returns', async () => {
    const router = await renderApp({ at: '/skills-hub' });
    await clickAsync(screen.getByTitle('Skills Hub settings'));
    expect(router.state.location.pathname).toBe('/skills-hub/settings');
    await clickAsync(await screen.findByTitle('Back to Skills Hub'));
    expect(router.state.location.pathname).toBe('/skills-hub');
  });
});

describe('WithTranslations', () => {
  it('registers English at once and follows the host language from its own locale files', async () => {
    const i18n = createI18n({ locale: 'en', resources: {} });
    await act(async () => {
      render(
        <I18nProvider i18n={i18n}>
          <WithTranslations>
            <p>child</p>
          </WithTranslations>
        </I18nProvider>,
      );
    });
    expect(i18n.hasResourceBundle('en', 'skillsHub')).toBe(true);
    for (const language of ['de', 'es', 'nl']) {
      await act(async () => {
        await i18n.changeLanguage(language);
      });
      await waitFor(() => expect(i18n.hasResourceBundle(language, 'skillsHub')).toBe(true));
    }
  });
});
